package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.event.DocumentVersionUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "intellidoc.ingestion", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpIngestionMessagePublisher implements IngestionMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(NoOpIngestionMessagePublisher.class);

    @Override
    public void publish(DocumentVersionUploadedEvent event) {
        log.info(
                "Skipping ingestion queue publish for document {} version {} because ingestion is disabled.",
                event.documentId(),
                event.versionNumber());
    }
}
