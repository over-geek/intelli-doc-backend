package com.intellidoc.ingestion.repository;

import com.intellidoc.ingestion.model.DocumentChunkEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    void deleteByDocumentVersionId(UUID documentVersionId);

    List<DocumentChunkEntity> findByDocumentVersionIdOrderByChunkIndexAsc(UUID documentVersionId);
}
