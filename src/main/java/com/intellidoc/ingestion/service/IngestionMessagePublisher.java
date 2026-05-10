package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.event.DocumentVersionUploadedEvent;

public interface IngestionMessagePublisher {

    void publish(DocumentVersionUploadedEvent event);
}
