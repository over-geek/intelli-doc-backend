package com.intellidoc.ingestion.service;

import com.intellidoc.admin.model.DocumentProcessingStatus;
import com.intellidoc.admin.model.DocumentVersionEntity;
import com.intellidoc.admin.repository.DocumentVersionRepository;
import com.intellidoc.ingestion.model.IngestionQueueMessage;
import com.intellidoc.ingestion.model.IngestionWorkItem;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(IngestionOrchestratorService.class);

    private final DocumentVersionRepository documentVersionRepository;
    private final IngestionPipelineExecutor ingestionPipelineExecutor;

    public IngestionOrchestratorService(
            DocumentVersionRepository documentVersionRepository,
            IngestionPipelineExecutor ingestionPipelineExecutor) {
        this.documentVersionRepository = documentVersionRepository;
        this.ingestionPipelineExecutor = ingestionPipelineExecutor;
    }

    @Transactional
    public void process(IngestionQueueMessage message) {
        DocumentVersionEntity version = loadDocumentVersion(message.documentVersionId());
        validateRelationship(version, message.documentId());
        validateVersionNumber(version, message.versionNumber());

        if (version.getProcessingStatus() == DocumentProcessingStatus.READY) {
            log.info(
                    "Skipping duplicate ingestion message for document {} version {} because it is already READY.",
                    message.documentId(),
                    message.versionNumber());
            return;
        }

        IngestionWorkItem workItem = new IngestionWorkItem(
                message.documentId(),
                message.documentVersionId(),
                message.versionNumber(),
                message.documentTitle(),
                message.documentSlug(),
                message.blobPath(),
                message.blobVersionId(),
                message.fileName(),
                message.fileType(),
                message.uploadedBy(),
                message.requestedAt(),
                version);

        log.info(
                "Starting ingestion orchestration for document {} version {} from blob {}",
                message.documentId(),
                message.versionNumber(),
                message.blobPath());
        ingestionPipelineExecutor.execute(workItem);
    }

    private DocumentVersionEntity loadDocumentVersion(UUID documentVersionId) {
        return documentVersionRepository.findById(documentVersionId)
                .orElseThrow(() -> new NonRetryableIngestionException(
                        "Document version %s was not found for ingestion.".formatted(documentVersionId)));
    }

    private void validateRelationship(DocumentVersionEntity version, UUID documentId) {
        if (!version.getDocument().getId().equals(documentId)) {
            throw new NonRetryableIngestionException(
                    "Document version %s does not belong to document %s."
                            .formatted(version.getId(), documentId));
        }
    }

    private void validateVersionNumber(DocumentVersionEntity version, int versionNumber) {
        if (version.getVersionNumber() != versionNumber) {
            throw new NonRetryableIngestionException(
                    "Document version %s expected version number %s but received %s."
                            .formatted(version.getId(), version.getVersionNumber(), versionNumber));
        }
    }
}
