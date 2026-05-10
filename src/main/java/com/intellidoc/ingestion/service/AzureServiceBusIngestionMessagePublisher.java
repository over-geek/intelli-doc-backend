package com.intellidoc.ingestion.service;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.ingestion.event.DocumentVersionUploadedEvent;
import com.intellidoc.ingestion.model.IngestionQueueMessage;
import com.intellidoc.shared.error.BadRequestException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ServiceBusSenderClient.class)
public class AzureServiceBusIngestionMessagePublisher implements IngestionMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(AzureServiceBusIngestionMessagePublisher.class);

    private final ServiceBusSenderClient senderClient;
    private final ObjectMapper objectMapper;
    private final IntelliDocProperties properties;

    public AzureServiceBusIngestionMessagePublisher(
            ServiceBusSenderClient senderClient,
            ObjectMapper objectMapper,
            IntelliDocProperties properties) {
        this.senderClient = senderClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(DocumentVersionUploadedEvent event) {
        IngestionQueueMessage payload = new IngestionQueueMessage(
                event.documentId(),
                event.documentVersionId(),
                event.versionNumber(),
                event.documentTitle(),
                event.documentSlug(),
                event.blobPath(),
                event.blobVersionId(),
                event.fileName(),
                event.fileType(),
                event.uploadedBy(),
                event.occurredAt());

        ServiceBusMessage message = new ServiceBusMessage(serialize(payload))
                .setContentType("application/json")
                .setSubject("intellidoc.document-version.uploaded")
                .setMessageId(buildMessageId(event.documentVersionId()))
                .setCorrelationId(event.documentId().toString());
        message.getApplicationProperties().put("documentId", event.documentId().toString());
        message.getApplicationProperties().put("documentVersionId", event.documentVersionId().toString());
        message.getApplicationProperties().put("versionNumber", event.versionNumber());
        message.getApplicationProperties().put("queueName", properties.getIngestion().getQueueName());

        senderClient.sendMessage(message);
        log.info(
                "Published ingestion message for document {} version {} to queue {}",
                event.documentId(),
                event.versionNumber(),
                properties.getIngestion().getQueueName());
    }

    private String serialize(IngestionQueueMessage payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            String message = exception.getOriginalMessage() != null
                    ? exception.getOriginalMessage()
                    : exception.getMessage();
            throw new BadRequestException(
                    "ingestion_message_serialization_failed",
                    "Failed to serialize the ingestion queue message: " + message);
        }
    }

    private String buildMessageId(UUID documentVersionId) {
        return "document-version-uploaded:%s".formatted(documentVersionId);
    }
}
