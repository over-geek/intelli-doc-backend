package com.intellidoc.ingestion.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.intellidoc.config.IntelliDocProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class IngestionMessagingConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "intellidoc.ingestion", name = "enabled", havingValue = "true")
    ServiceBusSenderClient ingestionQueueSenderClient(IntelliDocProperties properties) {
        String connectionString = properties.getIngestion().getConnectionString();
        String queueName = properties.getIngestion().getQueueName();

        if (!StringUtils.hasText(connectionString)) {
            throw new IllegalStateException(
                    "Service Bus ingestion is enabled but no connection string is configured.");
        }
        if (!StringUtils.hasText(queueName)) {
            throw new IllegalStateException(
                    "Service Bus ingestion is enabled but no queue name is configured.");
        }

        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .queueName(queueName.trim())
                .buildClient();
    }
}
