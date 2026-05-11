package com.intellidoc.rag.service;

import java.util.UUID;

public interface QueryRewriteService {

    QueryRewriteResult rewriteQuery(String userEmail, UUID sessionId, String query);

    record QueryRewriteResult(
            String originalQuery,
            String rewrittenQuery,
            boolean rewritten,
            boolean modelUsed,
            String reason) {
    }
}
