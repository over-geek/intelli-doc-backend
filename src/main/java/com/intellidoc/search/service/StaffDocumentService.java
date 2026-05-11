package com.intellidoc.search.service;

import com.azure.search.documents.SearchClient;
import com.azure.search.documents.models.QueryType;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.SemanticSearchOptions;
import com.intellidoc.admin.model.DocumentAccessPolicyEntity;
import com.intellidoc.admin.model.DocumentEntity;
import com.intellidoc.admin.model.DocumentStatus;
import com.intellidoc.admin.model.DocumentVersionEntity;
import com.intellidoc.admin.repository.DocumentAccessPolicyRepository;
import com.intellidoc.admin.repository.DocumentRepository;
import com.intellidoc.admin.repository.DocumentVersionRepository;
import com.intellidoc.admin.storage.DocumentStorageService;
import com.intellidoc.security.model.AuthenticatedUser;
import com.intellidoc.shared.error.BadRequestException;
import com.intellidoc.shared.error.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StaffDocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentAccessPolicyRepository documentAccessPolicyRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentAccessEvaluator documentAccessEvaluator;
    private final SearchClient searchClient;
    private final SearchSecurityFilterBuilder searchSecurityFilterBuilder;
    private final com.intellidoc.config.IntelliDocProperties properties;

    public StaffDocumentService(
            DocumentRepository documentRepository,
            DocumentAccessPolicyRepository documentAccessPolicyRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentStorageService documentStorageService,
            DocumentAccessEvaluator documentAccessEvaluator,
            SearchClient searchClient,
            SearchSecurityFilterBuilder searchSecurityFilterBuilder,
            com.intellidoc.config.IntelliDocProperties properties) {
        this.documentRepository = documentRepository;
        this.documentAccessPolicyRepository = documentAccessPolicyRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentStorageService = documentStorageService;
        this.documentAccessEvaluator = documentAccessEvaluator;
        this.searchClient = searchClient;
        this.searchSecurityFilterBuilder = searchSecurityFilterBuilder;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<StaffDocumentSummaryView> listAccessibleDocuments(
            AuthenticatedUser user, String search, UUID categoryId, UUID departmentId) {
        return documentRepository.findAll(accessiblePublishedSpecification(search, categoryId, departmentId)).stream()
                .filter(document -> canAccess(user, document.getId()))
                .sorted(Comparator.comparing(DocumentEntity::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DocumentEntity::getUpdatedAt, Comparator.reverseOrder()))
                .map(this::toSummaryView)
                .toList();
    }

    @Transactional(readOnly = true)
    public StaffDocumentDetailView getAccessibleDocument(AuthenticatedUser user, UUID documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException(
                        "staff_document_not_found",
                        "The requested document does not exist."));
        if (document.getStatus() != DocumentStatus.PUBLISHED || !canAccess(user, documentId)) {
            throw new NotFoundException(
                    "staff_document_not_found",
                    "The requested document does not exist or is not accessible to the current user.");
        }

        DocumentVersionEntity currentVersion = documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, document.getCurrentVersion())
                .orElseThrow(() -> new NotFoundException(
                        "staff_document_version_not_found",
                        "The current published document version could not be found."));

        return new StaffDocumentDetailView(
                toSummaryView(document),
                new StaffDocumentVersionView(
                        currentVersion.getId(),
                        currentVersion.getVersionNumber(),
                        currentVersion.getFileName(),
                        currentVersion.getFileType().name(),
                        currentVersion.getFileSizeBytes(),
                        currentVersion.getEffectiveDate(),
                        currentVersion.getCreatedAt(),
                        currentVersion.getTotalPages(),
                        currentVersion.getTotalChunks()));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> openCurrentDocument(AuthenticatedUser user, UUID documentId) {
        StaffDocumentDetailView detailView = getAccessibleDocument(user, documentId);
        DocumentVersionEntity currentVersion = documentVersionRepository
                .findByDocumentIdAndVersionNumber(documentId, detailView.document().currentVersion())
                .orElseThrow(() -> new NotFoundException(
                        "staff_document_version_not_found",
                        "The current published document version could not be found."));

        try {
            InputStream stream =
                    documentStorageService.openStream(currentVersion.getBlobPath(), currentVersion.getBlobVersionId());
            MediaType contentType = switch (currentVersion.getFileType().name()) {
                case "PDF" -> MediaType.APPLICATION_PDF;
                case "DOCX" -> MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                default -> MediaType.APPLICATION_OCTET_STREAM;
            };

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(currentVersion.getFileName())
                            .build()
                            .toString())
                    .body(new InputStreamResource(stream));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open the requested document binary.", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<StaffSearchResultView> keywordSearch(AuthenticatedUser user, String query) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("staff_search_query_required", "A search query is required.");
        }

        SearchOptions searchOptions = new SearchOptions()
                .setFilter(searchSecurityFilterBuilder.buildFilter(user))
                .setTop(properties.getSearch().getTopK())
                .setSearchFields(new String[] {"document_title", "content", "section_heading", "tags"})
                .setSelect(new String[] {
                    "id",
                    "document_id",
                    "document_version_id",
                    "document_title",
                    "content",
                    "page_number",
                    "section_heading",
                    "category",
                    "department",
                    "effective_date",
                    "status"
                })
                .setQueryType(QueryType.SEMANTIC)
                .setSemanticSearchOptions(new SemanticSearchOptions()
                        .setSemanticConfigurationName(properties.getSearch().getSemanticConfiguration()));

        List<StaffSearchResultView> results = new ArrayList<>();
        for (SearchResult result : searchClient.search(query.trim(), searchOptions, null)) {
            Map<String, Object> document = result.getDocument(Map.class);
            results.add(new StaffSearchResultView(
                    stringValue(document.get("id")),
                    stringValue(document.get("document_id")),
                    stringValue(document.get("document_version_id")),
                    stringValue(document.get("document_title")),
                    stringValue(document.get("content")),
                    stringValue(document.get("section_heading")),
                    integerValue(document.get("page_number")),
                    stringValue(document.get("category")),
                    stringValue(document.get("department")),
                    offsetDateTimeValue(document.get("effective_date")),
                    stringValue(document.get("status")),
                    result.getScore(),
                    result.getSemanticSearch() == null ? null : result.getSemanticSearch().getRerankerScore()));
        }
        return results;
    }

    private Specification<DocumentEntity> accessiblePublishedSpecification(String search, UUID categoryId, UUID departmentId) {
        return (root, queryObj, builder) -> {
            root.fetch("category");
            root.fetch("department");
            queryObj.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), DocumentStatus.PUBLISHED));
            if (StringUtils.hasText(search)) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("slug")), pattern)));
            }
            if (categoryId != null) {
                predicates.add(builder.equal(root.get("category").get("id"), categoryId));
            }
            if (departmentId != null) {
                predicates.add(builder.equal(root.get("department").get("id"), departmentId));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private boolean canAccess(AuthenticatedUser user, UUID documentId) {
        List<DocumentAccessPolicyEntity> accessPolicies =
                documentAccessPolicyRepository.findByDocumentIdOrderByAccessTypeAscAccessValueAsc(documentId);
        return documentAccessEvaluator.canAccess(user, accessPolicies);
    }

    private StaffDocumentSummaryView toSummaryView(DocumentEntity document) {
        return new StaffDocumentSummaryView(
                document.getId(),
                document.getTitle(),
                document.getSlug(),
                document.getCategory().getId(),
                document.getCategory().getName(),
                document.getDepartment().getId(),
                document.getDepartment().getName(),
                document.getCurrentVersion(),
                document.getTags(),
                document.getPublishedAt(),
                document.getUpdatedAt());
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            return Integer.valueOf(string);
        }
        return null;
    }

    private OffsetDateTime offsetDateTimeValue(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof String string && StringUtils.hasText(string)) {
            return OffsetDateTime.parse(string);
        }
        return null;
    }

    public record StaffDocumentSummaryView(
            UUID id,
            String title,
            String slug,
            UUID categoryId,
            String categoryName,
            UUID departmentId,
            String departmentName,
            int currentVersion,
            List<String> tags,
            Instant publishedAt,
            Instant updatedAt) {
    }

    public record StaffDocumentVersionView(
            UUID id,
            int versionNumber,
            String fileName,
            String fileType,
            long fileSizeBytes,
            java.time.LocalDate effectiveDate,
            Instant createdAt,
            int totalPages,
            int totalChunks) {
    }

    public record StaffDocumentDetailView(
            StaffDocumentSummaryView document,
            StaffDocumentVersionView currentVersion) {
    }

    public record StaffSearchResultView(
            String chunkId,
            String documentId,
            String documentVersionId,
            String documentTitle,
            String content,
            String sectionHeading,
            Integer pageNumber,
            String category,
            String department,
            OffsetDateTime effectiveDate,
            String status,
            double searchScore,
            Double rerankerScore) {
    }
}
