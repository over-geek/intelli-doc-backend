package com.intellidoc.rag.controller;

import com.intellidoc.search.service.HybridRetrievalService;
import com.intellidoc.security.jwt.EntraJwtClaimsMapper;
import com.intellidoc.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/chat/sessions")
public class HybridRetrievalController {

    private final HybridRetrievalService hybridRetrievalService;
    private final EntraJwtClaimsMapper claimsMapper;

    public HybridRetrievalController(
            HybridRetrievalService hybridRetrievalService,
            EntraJwtClaimsMapper claimsMapper) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.claimsMapper = claimsMapper;
    }

    @PostMapping("/{sessionId}/retrieve")
    public ResponseEntity<HybridRetrievalService.RetrievalResponse> retrieve(
            @PathVariable UUID sessionId,
            @Valid @RequestBody RetrieveRequest request,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = claimsMapper.toAuthenticatedUser(jwt, authentication.getAuthorities());
        return ResponseEntity.ok(hybridRetrievalService.retrieve(user, sessionId, request.query()));
    }

    public record RetrieveRequest(@NotBlank String query) {
    }
}
