package com.intellidoc.admin.storage;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.intellidoc.config.IntelliDocProperties;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "intellidoc.storage", name = "enabled", havingValue = "true")
public class AzureBlobDocumentStorageService implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(AzureBlobDocumentStorageService.class);

    private final BlobContainerClient containerClient;

    public AzureBlobDocumentStorageService(BlobServiceClient blobServiceClient, IntelliDocProperties properties) {
        this.containerClient = blobServiceClient.getBlobContainerClient(properties.getStorage().getContainerName());
        if (!containerClient.exists()) {
            containerClient.create();
        }
    }

    @Override
    public StoredDocumentBlob store(
            String documentSlug,
            int versionNumber,
            String originalFilename,
            InputStream content,
            long contentLength,
            String contentType) throws IOException {
        String blobPath = buildBlobPath(documentSlug, versionNumber, originalFilename);
        BlobClient blobClient = containerClient.getBlobClient(blobPath);
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream");

        blobClient.upload(content, contentLength, true);
        blobClient.setHttpHeaders(headers);
        String blobVersionId = blobClient.getProperties().getVersionId();
        log.info("Stored document binary in Azure Blob Storage at {}", blobPath);
        return new StoredDocumentBlob(blobPath, blobVersionId);
    }

    @Override
    public InputStream openStream(String blobPath, String blobVersionId) throws IOException {
        BlobClient blobClient = containerClient.getBlobClient(blobPath);
        if (StringUtils.hasText(blobVersionId)) {
            blobClient = blobClient.getVersionClient(blobVersionId);
        }
        return blobClient.openInputStream();
    }

    private String buildBlobPath(String documentSlug, int versionNumber, String originalFilename) {
        String safeFilename = StringUtils.hasText(originalFilename)
                ? originalFilename.trim().replaceAll("[^A-Za-z0-9._-]", "-")
                : "document.bin";
        return "documents/%s/%s/v%s/%s".formatted(LocalDate.now().getYear(), documentSlug, versionNumber, safeFilename);
    }
}
