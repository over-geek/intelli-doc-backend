package com.intellidoc.ingestion.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.ingestion.service.IngestionWorkerMessageHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class IngestionWorkerConfiguration {

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnProperty(prefix = "intellidoc.ingestion", name = "worker-enabled", havingValue = "true")
    ServiceBusProcessorClient ingestionWorkerProcessorClient(
            IntelliDocProperties properties,
            IngestionWorkerMessageHandler ingestionWorkerMessageHandler) {
        IntelliDocProperties.Ingestion ingestion = properties.getIngestion();
        if (!StringUtils.hasText(ingestion.getConnectionString())) {
            throw new IllegalStateException(
                    "Service Bus ingestion worker is enabled but no connection string is configured.");
        }
        if (!StringUtils.hasText(ingestion.getQueueName())) {
            throw new IllegalStateException(
                    "Service Bus ingestion worker is enabled but no queue name is configured.");
        }

        return new ServiceBusClientBuilder()
                .connectionString(ingestion.getConnectionString())
                .processor()
                .queueName(ingestion.getQueueName())
                .disableAutoComplete()
                .maxConcurrentCalls(ingestion.getMaxConcurrentCalls())
                .processMessage(ingestionWorkerMessageHandler::handleMessage)
                .processError(ingestionWorkerMessageHandler::handleError)
                .buildProcessorClient();
    }
}
