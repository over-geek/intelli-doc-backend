package com.intellidoc.admin.repository;

import com.intellidoc.admin.model.DocumentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {

    boolean existsBySlugIgnoreCase(String slug);

    Optional<DocumentEntity> findBySlugIgnoreCase(String slug);
}
