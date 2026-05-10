package com.intellidoc.ingestion.service;

import com.intellidoc.admin.model.DocumentProcessingStatus;
import com.intellidoc.admin.repository.DocumentVersionRepository;
import com.intellidoc.ingestion.model.ChunkingResult;
import com.intellidoc.ingestion.model.ParsedDocumentLayout;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionProcessingStateService {

    private static final int MAX_ERROR_LENGTH = 2_000;

    private final DocumentVersionRepository documentVersionRepository;

    public IngestionProcessingStateService(DocumentVersionRepository documentVersionRepository) {
        this.documentVersionRepository = documentVersionRepository;
    }

    @Transactional
    public void markParsingStarted(UUID documentVersionId) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.PARSING);
            version.setProcessingError(null);
            documentVersionRepository.save(version);
        });
    }

    @Transactional
    public void recordParsingResult(UUID documentVersionId, ParsedDocumentLayout parsedLayout) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.CHUNKING);
            version.setProcessingError(null);
            version.setTotalPages(parsedLayout.totalPages());
            documentVersionRepository.save(version);
        });
    }

    @Transactional
    public void recordChunkingResult(UUID documentVersionId, ChunkingResult chunkingResult) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.EMBEDDING);
            version.setProcessingError(null);
            version.setTotalChunks(chunkingResult.chunks().size());
            documentVersionRepository.save(version);
        });
    }

    @Transactional
    public void recordEmbeddingResult(UUID documentVersionId) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.INDEXING);
            version.setProcessingError(null);
            documentVersionRepository.save(version);
        });
    }

    @Transactional
    public void markIndexingStarted(UUID documentVersionId) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.INDEXING);
            version.setProcessingError(null);
            documentVersionRepository.save(version);
        });
    }

    @Transactional
    public void markReady(UUID documentVersionId) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.READY);
            version.setProcessingError(null);
            documentVersionRepository.save(version);
        });
    }

    @Transactional
    public void markQueued(UUID documentVersionId) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.UPLOADED);
            version.setProcessingError(null);
            documentVersionRepository.save(version);
        });
    }

    @Transactional
    public void markFailed(UUID documentVersionId, String failureReason) {
        documentVersionRepository.findById(documentVersionId).ifPresent(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.FAILED);
            version.setProcessingError(truncate(failureReason));
            documentVersionRepository.save(version);
        });
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
