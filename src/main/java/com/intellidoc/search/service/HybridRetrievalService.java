package com.intellidoc.search.service;

import com.azure.search.documents.SearchClient;
import com.azure.search.documents.models.QueryType;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.SemanticSearchOptions;
import com.azure.search.documents.models.VectorFilterMode;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizedQuery;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.rag.service.QueryRewriteService;
import com.intellidoc.security.model.AuthenticatedUser;
import com.intellidoc.shared.error.BadRequestException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);

    private final SearchClient searchClient;
    private final SearchSecurityFilterBuilder searchSecurityFilterBuilder;
    private final QueryEmbeddingService queryEmbeddingService;
    private final QueryRewriteService queryRewriteService;
    private final IntelliDocProperties properties;

    public HybridRetrievalService(
            SearchClient searchClient,
            SearchSecurityFilterBuilder searchSecurityFilterBuilder,
            QueryEmbeddingService queryEmbeddingService,
            QueryRewriteService queryRewriteService,
            IntelliDocProperties properties) {
        this.searchClient = searchClient;
        this.searchSecurityFilterBuilder = searchSecurityFilterBuilder;
        this.queryEmbeddingService = queryEmbeddingService;
        this.queryRewriteService = queryRewriteService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RetrievalResponse retrieve(AuthenticatedUser user, UUID sessionId, String query) {
        if (!StringUtils.hasText(query)) {
            throw new BadRequestException("retrieval_query_required", "A query is required for retrieval.");
        }

        QueryRewriteService.QueryRewriteResult rewriteResult =
                queryRewriteService.rewriteQuery(user.email(), sessionId, query);
        String securityFilter = searchSecurityFilterBuilder.buildFilter(user);
        List<Float> queryVector = queryEmbeddingService.embedQuery(rewriteResult.rewrittenQuery());

        IntelliDocProperties.Search search = properties.getSearch();
        int candidateCount = Math.max(search.getRetrievalCandidateCount(), search.getTopK());

        VectorizedQuery vectorQuery = new VectorizedQuery(queryVector)
                .setFields(new String[] {"content_vector"})
                .setKNearestNeighborsCount(candidateCount)
                .setWeight(1.0f);

        SearchOptions searchOptions = new SearchOptions()
                .setFilter(securityFilter)
                .setIncludeTotalCount(true)
                .setTop(candidateCount)
                .setSearchFields(new String[] {
                    "document_title", "content", "section_heading", "tags", "category", "department"
                })
                .setSelect(new String[] {
                    "id",
                    "document_id",
                    "document_version_id",
                    "document_title",
                    "content",
                    "page_number",
                    "section_heading",
                    "chunk_index",
                    "category",
                    "department",
                    "effective_date",
                    "tags",
                    "allowed_roles",
                    "allowed_departments",
                    "allowed_users",
                    "status"
                })
                .setQueryType(QueryType.SEMANTIC)
                .setSemanticSearchOptions(new SemanticSearchOptions()
                        .setSemanticConfigurationName(search.getSemanticConfiguration()))
                .setVectorSearchOptions(new VectorSearchOptions()
                        .setFilterMode(VectorFilterMode.PRE_FILTER)
                        .setQueries(List.of(vectorQuery)));

        List<RetrievedChunk> results = new ArrayList<>();
        for (SearchResult result : searchClient.search(rewriteResult.rewrittenQuery(), searchOptions, null)) {
            Map<String, Object> document = result.getDocument(Map.class);
            Double rerankerScore = result.getSemanticSearch() == null
                    ? null
                    : result.getSemanticSearch().getRerankerScore();

            if (rerankerScore != null && rerankerScore < search.getMinimumRerankerScore()) {
                continue;
            }

            results.add(new RetrievedChunk(
                    stringValue(document.get("id")),
                    stringValue(document.get("document_id")),
                    stringValue(document.get("document_version_id")),
                    stringValue(document.get("document_title")),
                    stringValue(document.get("content")),
                    stringValue(document.get("section_heading")),
                    integerValue(document.get("page_number")),
                    integerValue(document.get("chunk_index")),
                    stringValue(document.get("category")),
                    stringValue(document.get("department")),
                    offsetDateTimeValue(document.get("effective_date")),
                    stringListValue(document.get("tags")),
                    stringListValue(document.get("allowed_roles")),
                    stringListValue(document.get("allowed_departments")),
                    stringListValue(document.get("allowed_users")),
                    stringValue(document.get("status")),
                    result.getScore(),
                    rerankerScore));
        }

        List<RetrievedChunk> finalResults = results.stream()
                .sorted(Comparator
                        .comparing(RetrievedChunk::rerankerScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RetrievedChunk::searchScore, Comparator.reverseOrder()))
                .limit(search.getTopK())
                .toList();

        log.info(
                "Hybrid retrieval returned {} candidates and {} final chunks for session {}.",
                results.size(),
                finalResults.size(),
                sessionId);

        return new RetrievalResponse(
                rewriteResult.originalQuery(),
                rewriteResult.rewrittenQuery(),
                rewriteResult.rewritten(),
                rewriteResult.modelUsed(),
                rewriteResult.reason(),
                securityFilter,
                finalResults);
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

    @SuppressWarnings("unchecked")
    private List<String> stringListValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof Object[] array) {
            List<String> values = new ArrayList<>(array.length);
            for (Object item : array) {
                values.add(String.valueOf(item));
            }
            return values;
        }
        return List.of();
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

    public record RetrievalResponse(
            String originalQuery,
            String rewrittenQuery,
            boolean rewritten,
            boolean rewriteModelUsed,
            String rewriteReason,
            String securityFilter,
            List<RetrievedChunk> results) {
    }

    public record RetrievedChunk(
            String id,
            String documentId,
            String documentVersionId,
            String documentTitle,
            String content,
            String sectionHeading,
            Integer pageNumber,
            Integer chunkIndex,
            String category,
            String department,
            OffsetDateTime effectiveDate,
            List<String> tags,
            List<String> allowedRoles,
            List<String> allowedDepartments,
            List<String> allowedUsers,
            String status,
            double searchScore,
            Double rerankerScore) {
    }
}
