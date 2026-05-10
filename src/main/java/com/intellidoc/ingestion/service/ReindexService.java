package com.intellidoc.ingestion.service;

import com.intellidoc.admin.repository.DocumentRepository;
import com.intellidoc.admin.repository.DocumentVersionRepository;
import com.intellidoc.ingestion.event.DocumentVersionUploadedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReindexService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final IngestionProcessingStateService ingestionProcessingStateService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ReindexService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            IngestionProcessingStateService ingestionProcessingStateService,
            ApplicationEventPublisher applicationEventPublisher) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.ingestionProcessingStateService = ingestionProcessingStateService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public void reindexDocument(UUID documentId) {
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new com.intellidoc.shared.error.NotFoundException(
                        "document_not_found",
                        "Document %s was not found.".formatted(documentId)));
        var version = documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, document.getCurrentVersion())
                .orElseThrow(() -> new com.intellidoc.shared.error.NotFoundException(
                        "document_version_not_found",
                        "Current version for document %s was not found.".formatted(documentId)));

        ingestionProcessingStateService.markQueued(version.getId());
        applicationEventPublisher.publishEvent(new DocumentVersionUploadedEvent(
                document.getId(),
                document.getTitle(),
                document.getSlug(),
                version.getId(),
                version.getVersionNumber(),
                version.getBlobPath(),
                version.getBlobVersionId(),
                version.getFileName(),
                version.getFileType().name(),
                version.getUploadedBy(),
                Instant.now()));
    }

    @Transactional
    public int reindexAllCurrentDocuments() {
        int count = 0;
        for (var document : documentRepository.findAll()) {
            reindexDocument(document.getId());
            count++;
        }
        return count;
    }
}
