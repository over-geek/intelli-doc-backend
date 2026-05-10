package com.intellidoc.ingestion.service;

import com.intellidoc.admin.model.DocumentProcessingStatus;
import com.intellidoc.admin.repository.DocumentVersionRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionDispatchFailureService {

    private static final Logger log = LoggerFactory.getLogger(IngestionDispatchFailureService.class);
    private static final int MAX_ERROR_LENGTH = 2_000;

    private final DocumentVersionRepository documentVersionRepository;

    public IngestionDispatchFailureService(DocumentVersionRepository documentVersionRepository) {
        this.documentVersionRepository = documentVersionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatchFailed(UUID documentVersionId, String failureReason) {
        documentVersionRepository.findById(documentVersionId).ifPresentOrElse(version -> {
            version.setProcessingStatus(DocumentProcessingStatus.FAILED);
            String safeFailureReason = failureReason == null || failureReason.isBlank()
                    ? "Unknown Service Bus publishing error."
                    : failureReason;
            version.setProcessingError(truncate("Failed to enqueue ingestion job: " + safeFailureReason));
            documentVersionRepository.save(version);
        }, () -> log.warn(
                "Unable to mark ingestion dispatch failure because document version {} was not found.",
                documentVersionId));
    }

    private String truncate(String failureReason) {
        return failureReason.length() <= MAX_ERROR_LENGTH
                ? failureReason
                : failureReason.substring(0, MAX_ERROR_LENGTH);
    }
}
