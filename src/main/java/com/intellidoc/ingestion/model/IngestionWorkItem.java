package com.intellidoc.ingestion.model;

import com.intellidoc.admin.model.DocumentVersionEntity;
import java.time.Instant;
import java.util.UUID;

public record IngestionWorkItem(
        UUID documentId,
        UUID documentVersionId,
        int versionNumber,
        String documentTitle,
        String documentSlug,
        String blobPath,
        String blobVersionId,
        String fileName,
        String fileType,
        String uploadedBy,
        Instant requestedAt,
        DocumentVersionEntity documentVersion) {
}
