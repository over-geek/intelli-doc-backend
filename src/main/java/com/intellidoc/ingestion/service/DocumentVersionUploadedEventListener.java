package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.event.DocumentVersionUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DocumentVersionUploadedEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentVersionUploadedEventListener.class);

    private final IngestionMessagePublisher ingestionMessagePublisher;
    private final IngestionDispatchFailureService ingestionDispatchFailureService;

    public DocumentVersionUploadedEventListener(
            IngestionMessagePublisher ingestionMessagePublisher,
            IngestionDispatchFailureService ingestionDispatchFailureService) {
        this.ingestionMessagePublisher = ingestionMessagePublisher;
        this.ingestionDispatchFailureService = ingestionDispatchFailureService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentVersionUploaded(DocumentVersionUploadedEvent event) {
        try {
            ingestionMessagePublisher.publish(event);
        } catch (RuntimeException exception) {
            ingestionDispatchFailureService.markDispatchFailed(event.documentVersionId(), exception.getMessage());
            log.error(
                    "Failed to publish ingestion message for document {} version {}",
                    event.documentId(),
                    event.versionNumber(),
                    exception);
        }
    }
}
