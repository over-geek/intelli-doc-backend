package com.intellidoc.admin.repository;

import com.intellidoc.admin.model.DocumentAccessPolicyEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAccessPolicyRepository extends JpaRepository<DocumentAccessPolicyEntity, UUID> {

    List<DocumentAccessPolicyEntity> findByDocumentIdOrderByAccessTypeAscAccessValueAsc(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
