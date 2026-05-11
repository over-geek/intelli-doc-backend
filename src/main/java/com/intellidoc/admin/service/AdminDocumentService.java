package com.intellidoc.admin.service;

import com.intellidoc.admin.model.CategoryEntity;
import com.intellidoc.admin.model.DepartmentEntity;
import com.intellidoc.admin.model.DocumentAccessPolicyEntity;
import com.intellidoc.admin.model.DocumentAccessType;
import com.intellidoc.admin.model.DocumentEntity;
import com.intellidoc.admin.model.DocumentFileType;
import com.intellidoc.admin.model.DocumentProcessingStatus;
import com.intellidoc.admin.model.DocumentStatus;
import com.intellidoc.admin.model.DocumentVersionEntity;
import com.intellidoc.admin.repository.DocumentAccessPolicyRepository;
import com.intellidoc.admin.repository.DocumentRepository;
import com.intellidoc.admin.repository.DocumentVersionRepository;
import com.intellidoc.admin.storage.DocumentStorageService;
import com.intellidoc.ingestion.event.DocumentVersionUploadedEvent;
import com.intellidoc.ingestion.service.ReindexService;
import com.intellidoc.shared.error.BadRequestException;
import com.intellidoc.shared.error.ConflictException;
import com.intellidoc.shared.error.NotFoundException;
import jakarta.persistence.criteria.JoinType;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminDocumentService {

    private static final Logger log = LoggerFactory.getLogger(AdminDocumentService.class);
    private static final Set<String> ALLOWED_FILE_EXTENSIONS = Set.of("pdf", "docx");

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentAccessPolicyRepository documentAccessPolicyRepository;
    private final ReferenceDataAdminService referenceDataAdminService;
    private final SlugService slugService;
    private final DocumentStorageService documentStorageService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ReindexService reindexService;

    public AdminDocumentService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentAccessPolicyRepository documentAccessPolicyRepository,
            ReferenceDataAdminService referenceDataAdminService,
            SlugService slugService,
            DocumentStorageService documentStorageService,
            ApplicationEventPublisher applicationEventPublisher,
            ReindexService reindexService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentAccessPolicyRepository = documentAccessPolicyRepository;
        this.referenceDataAdminService = referenceDataAdminService;
        this.slugService = slugService;
        this.documentStorageService = documentStorageService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.reindexService = reindexService;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryView> listDocuments(DocumentListCriteria criteria) {
        return documentRepository.findAll(buildSpecification(criteria)).stream()
                .sorted(Comparator.comparing(DocumentEntity::getUpdatedAt).reversed())
                .map(document -> toSummaryView(
                        document,
                        documentVersionRepository.findByDocumentIdAndVersionNumber(
                                        document.getId(), document.getCurrentVersion())
                                .orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentDetailView getDocument(UUID documentId) {
        DocumentEntity document = getDocumentEntity(documentId);
        List<DocumentVersionView> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId)
                .stream()
                .map(this::toVersionView)
                .toList();
        List<DocumentAccessPolicyView> accessPolicies = documentAccessPolicyRepository
                .findByDocumentIdOrderByAccessTypeAscAccessValueAsc(documentId)
                .stream()
                .map(this::toAccessPolicyView)
                .toList();

        return new DocumentDetailView(
                toDocumentView(document),
                versions,
                accessPolicies);
    }

    @Transactional
    public DocumentDetailView createDocument(CreateDocumentCommand command) {
        validateUpload(command.file());
        String title = required(command.title(), "Document title");
        CategoryEntity category = referenceDataAdminService.getCategoryEntity(command.categoryId());
        DepartmentEntity department = referenceDataAdminService.getDepartmentEntity(command.departmentId());
        validateCategoryAndDepartmentAreActive(category, department);
        String actorEmail = normalizeActorEmail(command.actorEmail());
        String slug = resolveUniqueSlug(command.slug(), title);
        List<String> tags = normalizeTags(command.tags());
        List<AccessPolicyCommand> accessPolicies = normalizePolicies(command.accessPolicies());

        DocumentEntity document = new DocumentEntity();
        document.setId(UUID.randomUUID());
        document.setTitle(title);
        document.setSlug(slug);
        document.setCategory(category);
        document.setDepartment(department);
        document.setStatus(DocumentStatus.DRAFT);
        document.setCurrentVersion(1);
        document.setUploadedBy(actorEmail);
        document.setTags(tags);

        DocumentStorageService.StoredDocumentBlob storedBlob = storeDocumentBinary(command.file(), slug, 1);

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setVersionNumber(1);
        version.setBlobPath(storedBlob.blobPath());
        version.setBlobVersionId(storedBlob.blobVersionId());
        version.setFileName(requiredFilename(command.file()));
        version.setFileType(resolveFileType(command.file().getOriginalFilename()));
        version.setFileSizeBytes(command.file().getSize());
        version.setEffectiveDate(command.effectiveDate());
        version.setChangeSummary(trimToNull(command.changeSummary()));
        version.setUploadedBy(actorEmail);
        version.setProcessingStatus(DocumentProcessingStatus.UPLOADED);
        version.setTotalChunks(0);
        version.setTotalPages(0);

        try {
            DocumentEntity savedDocument = documentRepository.save(document);
            version.setDocument(savedDocument);
            DocumentVersionEntity savedVersion = documentVersionRepository.save(version);
            replaceAccessPoliciesInternal(savedDocument, accessPolicies, actorEmail);
            publishDocumentVersionUploaded(savedDocument, savedVersion);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "document_unique_constraint_conflict",
                    "The document could not be created because one of its unique fields already exists.");
        }

        log.info("Created document {} with initial version 1 by {}", document.getId(), actorEmail);
        return getDocument(document.getId());
    }

    @Transactional
    public DocumentDetailView updateDocumentMetadata(UUID documentId, UpdateDocumentMetadataCommand command) {
        DocumentEntity document = getDocumentEntity(documentId);
        validateCategoryAndDepartmentAreActive(
                referenceDataAdminService.getCategoryEntity(command.categoryId()),
                referenceDataAdminService.getDepartmentEntity(command.departmentId()));
        document.setTitle(required(command.title(), "Document title"));
        document.setCategory(referenceDataAdminService.getCategoryEntity(command.categoryId()));
        document.setDepartment(referenceDataAdminService.getDepartmentEntity(command.departmentId()));
        document.setTags(normalizeTags(command.tags()));

        documentRepository.save(document);
        queueSearchRefresh(document, "metadata updated");
        log.info("Updated metadata for document {}", documentId);
        return getDocument(documentId);
    }

    @Transactional
    public DocumentVersionView uploadNewVersion(UUID documentId, UploadDocumentVersionCommand command) {
        DocumentEntity document = getDocumentEntity(documentId);
        validateUpload(command.file());
        String actorEmail = normalizeActorEmail(command.actorEmail());
        int nextVersionNumber = documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(existing -> existing.getVersionNumber() + 1)
                .orElse(1);

        DocumentStorageService.StoredDocumentBlob storedBlob =
                storeDocumentBinary(command.file(), document.getSlug(), nextVersionNumber);

        DocumentVersionEntity version = new DocumentVersionEntity();
        version.setDocument(document);
        version.setVersionNumber(nextVersionNumber);
        version.setBlobPath(storedBlob.blobPath());
        version.setBlobVersionId(storedBlob.blobVersionId());
        version.setFileName(requiredFilename(command.file()));
        version.setFileType(resolveFileType(command.file().getOriginalFilename()));
        version.setFileSizeBytes(command.file().getSize());
        version.setEffectiveDate(command.effectiveDate());
        version.setChangeSummary(trimToNull(command.changeSummary()));
        version.setUploadedBy(actorEmail);
        version.setProcessingStatus(DocumentProcessingStatus.UPLOADED);
        version.setTotalChunks(0);
        version.setTotalPages(0);

        DocumentVersionEntity saved = documentVersionRepository.save(version);
        publishDocumentVersionUploaded(document, saved);
        log.info("Uploaded version {} for document {} by {}", nextVersionNumber, documentId, actorEmail);
        return toVersionView(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionView> listVersions(UUID documentId) {
        getDocumentEntity(documentId);
        return documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .map(this::toVersionView)
                .toList();
    }

    @Transactional
    public DocumentDetailView setCurrentVersion(UUID documentId, UUID versionId) {
        DocumentEntity document = getDocumentEntity(documentId);
        DocumentVersionEntity version = documentVersionRepository.findByDocumentIdAndId(documentId, versionId)
                .orElseThrow(() -> new NotFoundException(
                        "document_version_not_found",
                        "Version %s was not found for document %s.".formatted(versionId, documentId)));

        document.setCurrentVersion(version.getVersionNumber());
        documentRepository.save(document);
        queueSearchRefresh(document, "current version changed");
        log.info("Set current version {} for document {}", version.getVersionNumber(), documentId);
        return getDocument(documentId);
    }

    @Transactional(readOnly = true)
    public List<DocumentAccessPolicyView> listAccessPolicies(UUID documentId) {
        getDocumentEntity(documentId);
        return documentAccessPolicyRepository.findByDocumentIdOrderByAccessTypeAscAccessValueAsc(documentId).stream()
                .map(this::toAccessPolicyView)
                .toList();
    }

    @Transactional
    public List<DocumentAccessPolicyView> replaceAccessPolicies(
            UUID documentId, List<AccessPolicyCommand> accessPolicies, String actorEmail) {
        DocumentEntity document = getDocumentEntity(documentId);
        replaceAccessPoliciesInternal(document, normalizePolicies(accessPolicies), normalizeActorEmail(actorEmail));
        queueSearchRefresh(document, "access policies updated");
        log.info("Replaced access policies for document {}", documentId);
        return listAccessPolicies(documentId);
    }

    @Transactional
    public DocumentDetailView transitionStatus(UUID documentId, DocumentStatus targetStatus) {
        DocumentEntity document = getDocumentEntity(documentId);
        DocumentStatus currentStatus = document.getStatus();
        if (currentStatus == targetStatus) {
            return getDocument(documentId);
        }

        if (!isAllowedTransition(currentStatus, targetStatus)) {
            throw new BadRequestException(
                    "invalid_document_status_transition",
                    "Invalid status transition from %s to %s.".formatted(currentStatus, targetStatus));
        }

        if (targetStatus == DocumentStatus.PUBLISHED) {
            document.setPublishedAt(Instant.now());
            document.setRetiredAt(null);
        } else if (targetStatus == DocumentStatus.RETIRED) {
            document.setRetiredAt(Instant.now());
        }

        document.setStatus(targetStatus);
        documentRepository.save(document);
        queueSearchRefresh(document, "status changed to " + targetStatus.name());
        log.info("Transitioned document {} from {} to {}", documentId, currentStatus, targetStatus);
        return getDocument(documentId);
    }

    @Transactional
    public void reindexDocument(UUID documentId) {
        reindexService.reindexDocument(documentId);
        log.info("Queued re-index for document {}", documentId);
    }

    @Transactional
    public int reindexAllDocuments() {
        int queued = reindexService.reindexAllCurrentDocuments();
        log.info("Queued re-index for {} documents", queued);
        return queued;
    }

    private void replaceAccessPoliciesInternal(
            DocumentEntity document, List<AccessPolicyCommand> accessPolicies, String actorEmail) {
        documentAccessPolicyRepository.deleteByDocumentId(document.getId());
        List<DocumentAccessPolicyEntity> entities = new ArrayList<>();
        for (AccessPolicyCommand policy : accessPolicies) {
            DocumentAccessPolicyEntity entity = new DocumentAccessPolicyEntity();
            entity.setDocument(document);
            entity.setAccessType(policy.accessType());
            entity.setAccessValue(policy.accessValue());
            entity.setGrantedBy(actorEmail);
            entities.add(entity);
        }
        documentAccessPolicyRepository.saveAll(entities);
    }

    private void publishDocumentVersionUploaded(DocumentEntity document, DocumentVersionEntity version) {
        applicationEventPublisher.publishEvent(new DocumentVersionUploadedEvent(
                document.getId(),
                document.getTitle(),
                document.getSlug(),
                version.getId(),
                version.getVersionNumber(),
                version.getBlobPath(),
                version.getBlobVersionId(),
                version.getFileName(),
                version.getFileType().name(),
                version.getUploadedBy(),
                Instant.now()));
    }

    private void queueSearchRefresh(DocumentEntity document, String reason) {
        if (document.getCurrentVersion() <= 0) {
            return;
        }
        reindexService.reindexDocument(document.getId());
        log.info(
                "Queued document {} for re-index because {}.",
                document.getId(),
                reason);
    }

    private Specification<DocumentEntity> buildSpecification(DocumentListCriteria criteria) {
        return (root, query, builder) -> {
            root.fetch("category", JoinType.LEFT);
            root.fetch("department", JoinType.LEFT);
            query.distinct(true);

            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(criteria.search())) {
                String pattern = "%" + criteria.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("slug")), pattern)));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.categoryId() != null) {
                predicates.add(builder.equal(root.get("category").get("id"), criteria.categoryId()));
            }
            if (criteria.departmentId() != null) {
                predicates.add(builder.equal(root.get("department").get("id"), criteria.departmentId()));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private DocumentEntity getDocumentEntity(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException(
                        "document_not_found",
                        "Document %s was not found.".formatted(documentId)));
    }

    private DocumentStorageService.StoredDocumentBlob storeDocumentBinary(
            MultipartFile file, String documentSlug, int versionNumber) {
        try {
            return documentStorageService.store(
                    documentSlug,
                    versionNumber,
                    requiredFilename(file),
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType());
        } catch (IOException exception) {
            throw new BadRequestException(
                    "document_storage_failed",
                    "Failed to store document binary: " + exception.getMessage());
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("document_file_required", "A PDF or DOCX file is required.");
        }
        if (file.getSize() <= 0) {
            throw new BadRequestException("document_file_empty", "Uploaded file size must be greater than zero.");
        }
        String extension = extractExtension(requiredFilename(file));
        if (!ALLOWED_FILE_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("document_file_type_invalid", "Only PDF and DOCX files are supported.");
        }
    }

    private String resolveUniqueSlug(String requestedSlug, String title) {
        String baseSlug = slugService.toSlug(StringUtils.hasText(requestedSlug) ? requestedSlug : title);
        String candidate = baseSlug;
        int counter = 2;
        while (documentRepository.existsBySlugIgnoreCase(candidate)) {
            candidate = baseSlug + "-" + counter;
            counter++;
        }
        return candidate;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(tag -> tag.replaceAll("\\s+", " "))
                .distinct()
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    private List<AccessPolicyCommand> normalizePolicies(List<AccessPolicyCommand> policies) {
        if (policies == null || policies.isEmpty()) {
            throw new BadRequestException("document_access_policy_required", "At least one access policy is required.");
        }

        Set<AccessPolicyCommand> normalized = new LinkedHashSet<>();
        for (AccessPolicyCommand policy : policies) {
            if (policy == null || policy.accessType() == null) {
                throw new BadRequestException(
                        "document_access_policy_invalid",
                        "Each access policy must include an access type.");
            }
            String accessValue = normalizeAccessValue(policy.accessType(), policy.accessValue());
            normalized.add(new AccessPolicyCommand(policy.accessType(), accessValue));
        }

        if (normalized.stream().anyMatch(policy -> policy.accessType() == DocumentAccessType.ALL)) {
            return List.of(new AccessPolicyCommand(DocumentAccessType.ALL, "ALL"));
        }

        return normalized.stream()
                .sorted(Comparator.comparing(AccessPolicyCommand::accessType).thenComparing(AccessPolicyCommand::accessValue))
                .toList();
    }

    private boolean isAllowedTransition(DocumentStatus currentStatus, DocumentStatus targetStatus) {
        return switch (currentStatus) {
            case DRAFT -> targetStatus == DocumentStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> targetStatus == DocumentStatus.PUBLISHED;
            case PUBLISHED -> targetStatus == DocumentStatus.RETIRED;
            case RETIRED -> false;
        };
    }

    private String normalizeActorEmail(String actorEmail) {
        return required(actorEmail, "Authenticated actor email").toLowerCase(Locale.ROOT);
    }

    private void validateCategoryAndDepartmentAreActive(CategoryEntity category, DepartmentEntity department) {
        if (!category.isActive()) {
            throw new BadRequestException(
                    "document_category_inactive",
                    "Selected category is inactive and cannot be assigned to a document.");
        }
        if (!department.isActive()) {
            throw new BadRequestException(
                    "document_department_inactive",
                    "Selected department is inactive and cannot be assigned to a document.");
        }
    }

    private String normalizeAccessValue(DocumentAccessType accessType, String accessValue) {
        return switch (accessType) {
            case ALL -> "ALL";
            case ROLE, DEPARTMENT -> required(accessValue, "Access policy value").toUpperCase(Locale.ROOT);
            case USER -> required(accessValue, "Access policy value").toLowerCase(Locale.ROOT);
        };
    }

    private String requiredFilename(MultipartFile file) {
        return required(file.getOriginalFilename(), "Uploaded file name");
    }

    private DocumentFileType resolveFileType(String fileName) {
        return switch (extractExtension(fileName)) {
            case "pdf" -> DocumentFileType.PDF;
            case "docx" -> DocumentFileType.DOCX;
            default -> throw new BadRequestException(
                    "document_file_type_invalid",
                    "Only PDF and DOCX files are supported.");
        };
    }

    private String extractExtension(String fileName) {
        int separatorIndex = fileName.lastIndexOf('.');
        if (separatorIndex < 0 || separatorIndex == fileName.length() - 1) {
            throw new BadRequestException(
                    "document_file_extension_missing",
                    "Uploaded file must include a supported extension.");
        };
        return fileName.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("invalid_document_request", label + " is required.");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DocumentSummaryView toSummaryView(DocumentEntity document, DocumentVersionEntity currentVersion) {
        return new DocumentSummaryView(
                document.getId(),
                document.getTitle(),
                document.getSlug(),
                document.getStatus(),
                document.getCurrentVersion(),
                document.getCategory().getId(),
                document.getCategory().getName(),
                document.getDepartment().getId(),
                document.getDepartment().getName(),
                document.getTags(),
                document.getUploadedBy(),
                document.getUpdatedAt(),
                currentVersion == null ? null : toVersionView(currentVersion));
    }

    private DocumentView toDocumentView(DocumentEntity document) {
        return new DocumentView(
                document.getId(),
                document.getTitle(),
                document.getSlug(),
                document.getStatus(),
                document.getCurrentVersion(),
                document.getCategory().getId(),
                document.getCategory().getName(),
                document.getDepartment().getId(),
                document.getDepartment().getName(),
                document.getTags(),
                document.getUploadedBy(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getPublishedAt(),
                document.getRetiredAt());
    }

    private DocumentVersionView toVersionView(DocumentVersionEntity version) {
        return new DocumentVersionView(
                version.getId(),
                version.getVersionNumber(),
                version.getBlobPath(),
                version.getBlobVersionId(),
                version.getFileName(),
                version.getFileType(),
                version.getFileSizeBytes(),
                version.getEffectiveDate(),
                version.getChangeSummary(),
                version.getUploadedBy(),
                version.getProcessingStatus(),
                version.getProcessingError(),
                version.getTotalChunks(),
                version.getTotalPages(),
                version.getCreatedAt());
    }

    private DocumentAccessPolicyView toAccessPolicyView(DocumentAccessPolicyEntity entity) {
        return new DocumentAccessPolicyView(
                entity.getId(),
                entity.getAccessType(),
                entity.getAccessValue(),
                entity.getGrantedBy(),
                entity.getCreatedAt());
    }

    public record AccessPolicyCommand(DocumentAccessType accessType, String accessValue) {
    }

    public record CreateDocumentCommand(
            String title,
            String slug,
            UUID categoryId,
            UUID departmentId,
            List<String> tags,
            LocalDate effectiveDate,
            String changeSummary,
            List<AccessPolicyCommand> accessPolicies,
            MultipartFile file,
            String actorEmail) {
    }

    public record UpdateDocumentMetadataCommand(
            String title,
            UUID categoryId,
            UUID departmentId,
            List<String> tags) {
    }

    public record UploadDocumentVersionCommand(
            LocalDate effectiveDate,
            String changeSummary,
            MultipartFile file,
            String actorEmail) {
    }

    public record DocumentListCriteria(
            String search,
            DocumentStatus status,
            UUID categoryId,
            UUID departmentId) {
    }

    public record DocumentSummaryView(
            UUID id,
            String title,
            String slug,
            DocumentStatus status,
            int currentVersion,
            UUID categoryId,
            String categoryName,
            UUID departmentId,
            String departmentName,
            List<String> tags,
            String uploadedBy,
            Instant updatedAt,
            DocumentVersionView currentVersionDetails) {
    }

    public record DocumentDetailView(
            DocumentView document,
            List<DocumentVersionView> versions,
            List<DocumentAccessPolicyView> accessPolicies) {
    }

    public record DocumentView(
            UUID id,
            String title,
            String slug,
            DocumentStatus status,
            int currentVersion,
            UUID categoryId,
            String categoryName,
            UUID departmentId,
            String departmentName,
            List<String> tags,
            String uploadedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt,
            Instant retiredAt) {
    }

    public record DocumentVersionView(
            UUID id,
            int versionNumber,
            String blobPath,
            String blobVersionId,
            String fileName,
            DocumentFileType fileType,
            long fileSizeBytes,
            LocalDate effectiveDate,
            String changeSummary,
            String uploadedBy,
            DocumentProcessingStatus processingStatus,
            String processingError,
            int totalChunks,
            int totalPages,
            Instant createdAt) {
    }

    public record DocumentAccessPolicyView(
            UUID id,
            DocumentAccessType accessType,
            String accessValue,
            String grantedBy,
            Instant createdAt) {
    }
}
