package com.intellidoc.ingestion.event;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionUploadedEvent(
        UUID documentId,
        String documentTitle,
        String documentSlug,
        UUID documentVersionId,
        int versionNumber,
        String blobPath,
        String blobVersionId,
        String fileName,
        String fileType,
        String uploadedBy,
        Instant occurredAt) {
}
