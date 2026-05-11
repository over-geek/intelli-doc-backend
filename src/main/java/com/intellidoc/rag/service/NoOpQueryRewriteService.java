package com.intellidoc.rag.service;

import com.intellidoc.shared.error.BadRequestException;
import java.util.UUID;
import org.springframework.util.StringUtils;

public class NoOpQueryRewriteService implements QueryRewriteService {

    @Override
    public QueryRewriteResult rewriteQuery(String userEmail, UUID sessionId, String query) {
        String normalized = query == null ? null : query.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BadRequestException("query_rewrite_query_required", "A query is required for rewriting.");
        }
        return new QueryRewriteResult(normalized, normalized, false, false, "chat_model_unavailable");
    }
}
