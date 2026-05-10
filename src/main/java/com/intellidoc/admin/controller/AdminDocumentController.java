package com.intellidoc.admin.controller;

import com.intellidoc.admin.model.DocumentAccessType;
import com.intellidoc.admin.model.DocumentStatus;
import com.intellidoc.admin.service.AdminDocumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/admin/documents")
public class AdminDocumentController {

    private final AdminDocumentService adminDocumentService;

    public AdminDocumentController(AdminDocumentService adminDocumentService) {
        this.adminDocumentService = adminDocumentService;
    }

    @GetMapping
    public ResponseEntity<List<AdminDocumentService.DocumentSummaryView>> listDocuments(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) DocumentStatus status,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "departmentId", required = false) UUID departmentId) {
        return ResponseEntity.ok(adminDocumentService.listDocuments(
                new AdminDocumentService.DocumentListCriteria(search, status, categoryId, departmentId)));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<AdminDocumentService.DocumentDetailView> getDocument(@PathVariable UUID documentId) {
        return ResponseEntity.ok(adminDocumentService.getDocument(documentId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminDocumentService.DocumentDetailView> createDocument(
            @Valid @ModelAttribute CreateDocumentRequest request,
            Authentication authentication) {
        AdminDocumentService.DocumentDetailView created = adminDocumentService.createDocument(
                new AdminDocumentService.CreateDocumentCommand(
                        request.title(),
                        request.slug(),
                        request.categoryId(),
                        request.departmentId(),
                        request.tags(),
                        request.effectiveDate(),
                        request.changeSummary(),
                        mapPolicies(request.accessPolicies()),
                        request.file(),
                        authentication.getName()));
        return ResponseEntity.created(URI.create("/api/admin/documents/" + created.document().id())).body(created);
    }

    @PutMapping("/{documentId}/metadata")
    public ResponseEntity<AdminDocumentService.DocumentDetailView> updateMetadata(
            @PathVariable UUID documentId,
            @Valid @RequestBody UpdateDocumentMetadataRequest request) {
        return ResponseEntity.ok(adminDocumentService.updateDocumentMetadata(
                documentId,
                new AdminDocumentService.UpdateDocumentMetadataCommand(
                        request.title(),
                        request.categoryId(),
                        request.departmentId(),
                        request.tags())));
    }

    @PostMapping(path = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminDocumentService.DocumentVersionView> uploadVersion(
            @PathVariable UUID documentId,
            @Valid @ModelAttribute UploadVersionRequest request,
            Authentication authentication) {
        AdminDocumentService.DocumentVersionView created = adminDocumentService.uploadNewVersion(
                documentId,
                new AdminDocumentService.UploadDocumentVersionCommand(
                        request.effectiveDate(),
                        request.changeSummary(),
                        request.file(),
                        authentication.getName()));
        return ResponseEntity.created(
                        URI.create("/api/admin/documents/" + documentId + "/versions/" + created.id()))
                .body(created);
    }

    @GetMapping("/{documentId}/versions")
    public ResponseEntity<List<AdminDocumentService.DocumentVersionView>> listVersions(@PathVariable UUID documentId) {
        return ResponseEntity.ok(adminDocumentService.listVersions(documentId));
    }

    @PutMapping("/{documentId}/versions/{versionId}/current")
    public ResponseEntity<AdminDocumentService.DocumentDetailView> setCurrentVersion(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId) {
        return ResponseEntity.ok(adminDocumentService.setCurrentVersion(documentId, versionId));
    }

    @GetMapping("/{documentId}/access-policies")
    public ResponseEntity<List<AdminDocumentService.DocumentAccessPolicyView>> listAccessPolicies(
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(adminDocumentService.listAccessPolicies(documentId));
    }

    @PutMapping("/{documentId}/access-policies")
    public ResponseEntity<List<AdminDocumentService.DocumentAccessPolicyView>> replaceAccessPolicies(
            @PathVariable UUID documentId,
            @RequestBody List<@Valid AccessPolicyRequest> accessPolicies,
            Authentication authentication) {
        return ResponseEntity.ok(adminDocumentService.replaceAccessPolicies(
                documentId,
                mapPolicies(accessPolicies),
                authentication.getName()));
    }

    @PutMapping("/{documentId}/status")
    public ResponseEntity<AdminDocumentService.DocumentDetailView> transitionStatus(
            @PathVariable UUID documentId,
            @Valid @RequestBody StatusTransitionRequest request) {
        return ResponseEntity.ok(adminDocumentService.transitionStatus(documentId, request.status()));
    }

    @PostMapping("/{documentId}/reindex")
    public ResponseEntity<ReindexResponse> reindexDocument(@PathVariable UUID documentId) {
        adminDocumentService.reindexDocument(documentId);
        return ResponseEntity.accepted().body(new ReindexResponse(1));
    }

    @PostMapping("/reindex-all")
    public ResponseEntity<ReindexResponse> reindexAllDocuments() {
        int queued = adminDocumentService.reindexAllDocuments();
        return ResponseEntity.accepted().body(new ReindexResponse(queued));
    }

    private List<AdminDocumentService.AccessPolicyCommand> mapPolicies(List<AccessPolicyRequest> policies) {
        if (policies == null) {
            return List.of();
        }
        return policies.stream()
                .map(policy -> new AdminDocumentService.AccessPolicyCommand(policy.accessType(), policy.accessValue()))
                .toList();
    }

    public record CreateDocumentRequest(
            @NotBlank String title,
            String slug,
            UUID categoryId,
            UUID departmentId,
            List<String> tags,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            String changeSummary,
            List<AccessPolicyRequest> accessPolicies,
            MultipartFile file) {
    }

    public record UpdateDocumentMetadataRequest(
            @NotBlank String title,
            UUID categoryId,
            UUID departmentId,
            List<String> tags) {
    }

    public record UploadVersionRequest(
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveDate,
            String changeSummary,
            MultipartFile file) {
    }

    public record AccessPolicyRequest(DocumentAccessType accessType, String accessValue) {
    }

    public record StatusTransitionRequest(DocumentStatus status) {
    }

    public record ReindexResponse(int queuedDocuments) {
    }
}
