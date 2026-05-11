package com.intellidoc.rag.controller;

import com.intellidoc.rag.service.QueryRewriteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/chat/sessions")
public class QueryRewriteController {

    private final QueryRewriteService queryRewriteService;

    public QueryRewriteController(QueryRewriteService queryRewriteService) {
        this.queryRewriteService = queryRewriteService;
    }

    @PostMapping("/{sessionId}/rewrite-query")
    public ResponseEntity<QueryRewriteService.QueryRewriteResult> rewriteQuery(
            @PathVariable UUID sessionId,
            @Valid @RequestBody RewriteQueryRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                queryRewriteService.rewriteQuery(authentication.getName(), sessionId, request.query()));
    }

    public record RewriteQueryRequest(@NotBlank String query) {
    }
}
