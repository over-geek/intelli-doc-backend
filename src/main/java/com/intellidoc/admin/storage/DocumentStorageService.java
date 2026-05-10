package com.intellidoc.admin.storage;

import java.io.IOException;
import java.io.InputStream;

public interface DocumentStorageService {

    StoredDocumentBlob store(
            String documentSlug,
            int versionNumber,
            String originalFilename,
            InputStream content,
            long contentLength,
            String contentType) throws IOException;

    InputStream openStream(String blobPath, String blobVersionId) throws IOException;

    record StoredDocumentBlob(String blobPath, String blobVersionId) {
    }
}
