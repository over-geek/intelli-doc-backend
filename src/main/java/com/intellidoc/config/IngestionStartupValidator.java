package com.intellidoc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IngestionStartupValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionStartupValidator.class);

    private final IntelliDocProperties properties;

    public IngestionStartupValidator(IntelliDocProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        IntelliDocProperties.Ingestion ingestion = properties.getIngestion();
        if (!ingestion.isEnabled() && !ingestion.isWorkerEnabled()) {
            log.info("Service Bus ingestion publishing and worker are disabled for this environment.");
            return;
        }

        boolean hasConnectionString = StringUtils.hasText(ingestion.getConnectionString());
        log.info(
                "Service Bus ingestion configuration loaded for queue '{}' (publishingEnabled={}, workerEnabled={}, connectionStringConfigured={}, maxConcurrentCalls={}, documentIntelligenceEnabled={}, embeddingEnabled={}, embeddingBatchSize={})",
                ingestion.getQueueName(),
                ingestion.isEnabled(),
                ingestion.isWorkerEnabled(),
                hasConnectionString,
                ingestion.getMaxConcurrentCalls(),
                ingestion.getDocumentIntelligence().isEnabled(),
                ingestion.getEmbedding().isEnabled(),
                ingestion.getEmbedding().getBatchSize());
    }
}
