package com.intellidoc.ingestion.service;

import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.ingestion.model.IngestionQueueMessage;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class IngestionWorkerMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(IngestionWorkerMessageHandler.class);

    private final ObjectMapper objectMapper;
    private final IngestionOrchestratorService ingestionOrchestratorService;
    private final IngestionFailureClassifier ingestionFailureClassifier;
    private final IngestionMetricsService ingestionMetricsService;
    private final AdminIngestionFailureNotificationService adminIngestionFailureNotificationService;
    private final IntelliDocProperties properties;

    public IngestionWorkerMessageHandler(
            ObjectMapper objectMapper,
            IngestionOrchestratorService ingestionOrchestratorService,
            IngestionFailureClassifier ingestionFailureClassifier,
            IngestionMetricsService ingestionMetricsService,
            AdminIngestionFailureNotificationService adminIngestionFailureNotificationService,
            IntelliDocProperties properties) {
        this.objectMapper = objectMapper;
        this.ingestionOrchestratorService = ingestionOrchestratorService;
        this.ingestionFailureClassifier = ingestionFailureClassifier;
        this.ingestionMetricsService = ingestionMetricsService;
        this.adminIngestionFailureNotificationService = adminIngestionFailureNotificationService;
        this.properties = properties;
    }

    public void handleMessage(ServiceBusReceivedMessageContext context) {
        String payload = context.getMessage().getBody().toString();

        IngestionQueueMessage message = null;
        try {
            message = objectMapper.readValue(payload, IngestionQueueMessage.class);
            ingestionOrchestratorService.process(message);
            context.complete();
            ingestionMetricsService.recordMessageSucceeded();
            log.info(
                    "Completed ingestion worker message {} for document {} version {}",
                    context.getMessage().getMessageId(),
                    message.documentId(),
                    message.versionNumber());
        } catch (NonRetryableIngestionException | IOException exception) {
            deadLetter(context, "non-retryable", exception.getMessage());
            ingestionMetricsService.recordMessageDeadLettered();
            log.error(
                    "Dead-lettering non-retryable ingestion message {} because it cannot be processed safely. Payload={}",
                    context.getMessage().getMessageId(),
                    payload,
                    exception);
        } catch (RuntimeException exception) {
            int deliveryCount = Math.toIntExact(context.getMessage().getDeliveryCount());
            boolean nonRetryable = ingestionFailureClassifier.isNonRetryable(exception);
            boolean maxAttemptsReached = deliveryCount >= properties.getIngestion().getMaxDeliveryAttempts();

            if (nonRetryable || maxAttemptsReached) {
                deadLetter(
                        context,
                        nonRetryable ? "non-retryable" : "max-delivery-attempts",
                        exception.getMessage());
                ingestionMetricsService.recordMessageDeadLettered();
                if (message != null) {
                    adminIngestionFailureNotificationService.notifyAdmins(message, exception.getMessage());
                }
                log.error(
                        "Dead-lettering ingestion message {} after deliveryCount={} (nonRetryable={}, maxAttemptsReached={}).",
                        context.getMessage().getMessageId(),
                        deliveryCount,
                        nonRetryable,
                        maxAttemptsReached,
                        exception);
                return;
            }

            context.abandon();
            ingestionMetricsService.recordMessageRetried();
            log.error(
                    "Abandoning ingestion message {} so it can be retried (deliveryCount={}).",
                    context.getMessage().getMessageId(),
                    deliveryCount,
                    exception);
        }
    }

    public void handleError(ServiceBusErrorContext errorContext) {
        log.error(
                "Service Bus worker error. EntityPath={}, Namespace={}, ErrorSource={}",
                errorContext.getEntityPath(),
                errorContext.getFullyQualifiedNamespace(),
                errorContext.getErrorSource(),
                errorContext.getException());
    }

    private void deadLetter(ServiceBusReceivedMessageContext context, String reason, String description) {
        String safeDescription = description == null || description.isBlank()
                ? "No additional details provided."
                : description;
        DeadLetterOptions deadLetterOptions = new DeadLetterOptions()
                .setDeadLetterReason(reason)
                .setDeadLetterErrorDescription(safeDescription);
        context.deadLetter(deadLetterOptions);
    }
}
