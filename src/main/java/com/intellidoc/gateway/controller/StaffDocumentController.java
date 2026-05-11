package com.intellidoc.gateway.controller;

import com.intellidoc.search.service.StaffDocumentService;
import com.intellidoc.security.jwt.EntraJwtClaimsMapper;
import com.intellidoc.security.model.AuthenticatedUser;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffDocumentController {

    private final StaffDocumentService staffDocumentService;
    private final EntraJwtClaimsMapper claimsMapper;

    public StaffDocumentController(
            StaffDocumentService staffDocumentService,
            EntraJwtClaimsMapper claimsMapper) {
        this.staffDocumentService = staffDocumentService;
        this.claimsMapper = claimsMapper;
    }

    @GetMapping("/documents")
    public ResponseEntity<List<StaffDocumentService.StaffDocumentSummaryView>> listDocuments(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "categoryId", required = false) UUID categoryId,
            @RequestParam(name = "departmentId", required = false) UUID departmentId,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = claimsMapper.toAuthenticatedUser(jwt, authentication.getAuthorities());
        return ResponseEntity.ok(staffDocumentService.listAccessibleDocuments(user, search, categoryId, departmentId));
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<StaffDocumentService.StaffDocumentDetailView> getDocument(
            @PathVariable UUID documentId,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = claimsMapper.toAuthenticatedUser(jwt, authentication.getAuthorities());
        return ResponseEntity.ok(staffDocumentService.getAccessibleDocument(user, documentId));
    }

    @GetMapping("/documents/{documentId}/open")
    public ResponseEntity<InputStreamResource> openDocument(
            @PathVariable UUID documentId,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = claimsMapper.toAuthenticatedUser(jwt, authentication.getAuthorities());
        return staffDocumentService.openCurrentDocument(user, documentId);
    }

    @GetMapping("/search")
    public ResponseEntity<List<StaffDocumentService.StaffSearchResultView>> keywordSearch(
            @RequestParam("query") String query,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = claimsMapper.toAuthenticatedUser(jwt, authentication.getAuthorities());
        return ResponseEntity.ok(staffDocumentService.keywordSearch(user, query));
    }
}
