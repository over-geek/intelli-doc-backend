package com.intellidoc.admin.storage;

import com.intellidoc.config.IntelliDocProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "intellidoc.storage", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LocalDocumentStorageService implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorageService.class);

    private final Path rootPath;

    public LocalDocumentStorageService(IntelliDocProperties properties) {
        this.rootPath = Path.of(properties.getStorage().getLocalRoot()).toAbsolutePath().normalize();
    }

    @Override
    public StoredDocumentBlob store(
            String documentSlug,
            int versionNumber,
            String originalFilename,
            InputStream content,
            long contentLength,
            String contentType) throws IOException {
        Path blobPath = rootPath
                .resolve("documents")
                .resolve(String.valueOf(LocalDate.now().getYear()))
                .resolve(documentSlug)
                .resolve("v" + versionNumber)
                .resolve(sanitizeFilename(originalFilename));

        Files.createDirectories(blobPath.getParent());
        Files.copy(content, blobPath, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = rootPath.relativize(blobPath).toString().replace('\\', '/');
        String versionId = UUID.randomUUID().toString();
        log.info("Stored document binary locally at {} (contentType={}, size={})", relativePath, contentType, contentLength);
        return new StoredDocumentBlob(relativePath, versionId);
    }

    @Override
    public InputStream openStream(String blobPath, String blobVersionId) throws IOException {
        Path resolvedPath = rootPath.resolve(blobPath).normalize();
        if (!resolvedPath.startsWith(rootPath)) {
            throw new IOException("Refused to open a document outside the configured local storage root.");
        }
        if (!Files.exists(resolvedPath)) {
            throw new NoSuchFileException(resolvedPath.toString());
        }
        return Files.newInputStream(resolvedPath);
    }

    private String sanitizeFilename(String originalFilename) {
        String cleaned = StringUtils.hasText(originalFilename) ? originalFilename.trim() : "document.bin";
        return cleaned.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
