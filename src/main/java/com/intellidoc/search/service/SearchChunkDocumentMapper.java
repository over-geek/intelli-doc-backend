package com.intellidoc.search.service;

import com.intellidoc.admin.model.DocumentAccessPolicyEntity;
import com.intellidoc.admin.model.DocumentAccessType;
import com.intellidoc.admin.model.DocumentVersionEntity;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SearchChunkDocumentMapper {

    public Map<String, Object> map(
            DocumentChunkEntity chunk,
            DocumentVersionEntity documentVersion,
            List<DocumentAccessPolicyEntity> accessPolicies) {
        SecurityCollections securityCollections = buildSecurityCollections(accessPolicies);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", chunk.getId().toString());
        document.put("document_id", chunk.getDocument().getId().toString());
        document.put("document_version_id", documentVersion.getId().toString());
        document.put("document_title", chunk.getDocument().getTitle());
        document.put("content", chunk.getContent());
        document.put("content_vector", chunk.getEmbeddingVector());
        document.put("page_number", chunk.getPageNumber());
        document.put("section_heading", chunk.getSectionHeading());
        document.put("chunk_index", chunk.getChunkIndex());
        document.put("category", chunk.getDocument().getCategory().getName());
        document.put("department", chunk.getDocument().getDepartment().getCode());
        document.put("effective_date", toOffsetDateTime(documentVersion));
        document.put("tags", chunk.getDocument().getTags());
        document.put("allowed_roles", securityCollections.allowedRoles());
        document.put("allowed_departments", securityCollections.allowedDepartments());
        document.put("allowed_users", securityCollections.allowedUsers());
        document.put("status", chunk.getDocument().getStatus().name());
        return document;
    }

    private OffsetDateTime toOffsetDateTime(DocumentVersionEntity documentVersion) {
        if (documentVersion.getEffectiveDate() == null) {
            return null;
        }
        return documentVersion.getEffectiveDate().atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private SecurityCollections buildSecurityCollections(List<DocumentAccessPolicyEntity> accessPolicies) {
        Set<String> allowedRoles = new LinkedHashSet<>();
        Set<String> allowedDepartments = new LinkedHashSet<>();
        Set<String> allowedUsers = new LinkedHashSet<>();

        for (DocumentAccessPolicyEntity accessPolicy : accessPolicies) {
            if (accessPolicy.getAccessType() == DocumentAccessType.ALL) {
                allowedRoles.add("ALL");
            } else if (accessPolicy.getAccessType() == DocumentAccessType.ROLE) {
                allowedRoles.add(accessPolicy.getAccessValue());
            } else if (accessPolicy.getAccessType() == DocumentAccessType.DEPARTMENT) {
                allowedDepartments.add(accessPolicy.getAccessValue());
            } else if (accessPolicy.getAccessType() == DocumentAccessType.USER) {
                allowedUsers.add(accessPolicy.getAccessValue());
            }
        }

        return new SecurityCollections(
                new ArrayList<>(allowedRoles),
                new ArrayList<>(allowedDepartments),
                new ArrayList<>(allowedUsers));
    }

    private record SecurityCollections(
            List<String> allowedRoles,
            List<String> allowedDepartments,
            List<String> allowedUsers) {
    }
}
