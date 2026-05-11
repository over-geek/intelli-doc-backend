package com.intellidoc.rag.service;

import com.intellidoc.search.service.HybridRetrievalService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ContextAssemblyService {

    private static final int CONTEXT_TOKEN_BUDGET = 6000;
    private static final int APPROX_CHARS_PER_TOKEN = 4;

    public AssembledContext assemble(HybridRetrievalService.RetrievalResponse retrievalResponse) {
        List<HybridRetrievalService.RetrievedChunk> retrievedChunks = retrievalResponse.results();
        List<SourceContext> sourceContexts = deduplicate(retrievedChunks);

        List<SourceContext> selected = new ArrayList<>();
        int usedTokens = 0;
        Set<String> includedDocuments = new LinkedHashSet<>();

        for (SourceContext sourceContext : sourceContexts) {
            int estimatedTokens = estimateTokens(sourceContext.content()) + 40;
            if (!selected.isEmpty() && usedTokens + estimatedTokens > CONTEXT_TOKEN_BUDGET) {
                continue;
            }
            selected.add(sourceContext);
            includedDocuments.add(sourceContext.documentId());
            usedTokens += estimatedTokens;
        }

        if (includedDocuments.size() < 2) {
            for (SourceContext sourceContext : sourceContexts) {
                if (selected.contains(sourceContext)) {
                    continue;
                }
                if (includedDocuments.add(sourceContext.documentId())) {
                    selected.add(sourceContext);
                    break;
                }
            }
        }

        String formattedContext = formatContext(selected);
        return new AssembledContext(selected, formattedContext, usedTokens, CONTEXT_TOKEN_BUDGET);
    }

    private List<SourceContext> deduplicate(List<HybridRetrievalService.RetrievedChunk> retrievedChunks) {
        Map<String, SourceContext> deduped = new LinkedHashMap<>();
        int sourceNumber = 1;

        for (HybridRetrievalService.RetrievedChunk chunk : retrievedChunks) {
            String dedupeKey = chunk.documentId() + "|" + nullSafe(chunk.sectionHeading());
            SourceContext existing = deduped.get(dedupeKey);
            if (existing == null) {
                deduped.put(dedupeKey, toSourceContext(sourceNumber++, chunk));
                continue;
            }

            String mergedContent = existing.content() + System.lineSeparator() + System.lineSeparator() + chunk.content();
            deduped.put(dedupeKey, new SourceContext(
                    existing.sourceNumber(),
                    existing.chunkId(),
                    existing.documentId(),
                    existing.documentVersionId(),
                    existing.documentTitle(),
                    firstNonNull(existing.pageNumber(), chunk.pageNumber()),
                    existing.sectionHeading(),
                    mergedContent,
                    max(existing.searchScore(), chunk.searchScore()),
                    max(existing.rerankerScore(), chunk.rerankerScore()),
                    existing.category(),
                    existing.department(),
                    firstNonNull(existing.effectiveDate(), chunk.effectiveDate()),
                    existing.tags()));
        }

        return deduped.values().stream()
                .sorted((left, right) -> {
                    Double leftScore = left.rerankerScore() == null ? left.searchScore() : left.rerankerScore();
                    Double rightScore = right.rerankerScore() == null ? right.searchScore() : right.rerankerScore();
                    return rightScore.compareTo(leftScore);
                })
                .toList();
    }

    private SourceContext toSourceContext(int sourceNumber, HybridRetrievalService.RetrievedChunk chunk) {
        return new SourceContext(
                sourceNumber,
                chunk.id(),
                chunk.documentId(),
                chunk.documentVersionId(),
                chunk.documentTitle(),
                chunk.pageNumber(),
                chunk.sectionHeading(),
                chunk.content(),
                chunk.searchScore(),
                chunk.rerankerScore(),
                chunk.category(),
                chunk.department(),
                chunk.effectiveDate(),
                chunk.tags());
    }

    private String formatContext(List<SourceContext> selected) {
        StringBuilder builder = new StringBuilder();
        for (SourceContext source : selected) {
            builder.append("[SOURCE ")
                    .append(source.sourceNumber())
                    .append(" | Document: \"")
                    .append(source.documentTitle())
                    .append("\"");

            if (source.pageNumber() != null) {
                builder.append(" | Page: ").append(source.pageNumber());
            }
            if (source.sectionHeading() != null && !source.sectionHeading().isBlank()) {
                builder.append(" | Section: \"").append(source.sectionHeading()).append("\"");
            }
            builder.append("]").append(System.lineSeparator());
            builder.append(source.content()).append(System.lineSeparator());
            builder.append("[END SOURCE ").append(source.sourceNumber()).append("]")
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / APPROX_CHARS_PER_TOKEN);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private Integer firstNonNull(Integer left, Integer right) {
        return left != null ? left : right;
    }

    private OffsetDateTime firstNonNull(OffsetDateTime left, OffsetDateTime right) {
        return left != null ? left : right;
    }

    private Double max(Double left, Double right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return Math.max(left, right);
    }

    public record AssembledContext(
            List<SourceContext> sources,
            String formattedContext,
            int estimatedTokens,
            int tokenBudget) {
    }

    public record SourceContext(
            int sourceNumber,
            String chunkId,
            String documentId,
            String documentVersionId,
            String documentTitle,
            Integer pageNumber,
            String sectionHeading,
            String content,
            Double searchScore,
            Double rerankerScore,
            String category,
            String department,
            OffsetDateTime effectiveDate,
            List<String> tags) {
    }
}
