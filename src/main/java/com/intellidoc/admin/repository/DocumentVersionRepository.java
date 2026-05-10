package com.intellidoc.admin.repository;

import com.intellidoc.admin.model.DocumentVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, UUID> {

    List<DocumentVersionEntity> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersionEntity> findByDocumentIdAndVersionNumber(UUID documentId, int versionNumber);

    Optional<DocumentVersionEntity> findByDocumentIdAndId(UUID documentId, UUID id);

    Optional<DocumentVersionEntity> findFirstByDocumentIdOrderByVersionNumberDesc(UUID documentId);
}
