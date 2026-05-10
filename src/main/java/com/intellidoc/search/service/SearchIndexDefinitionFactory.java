package com.intellidoc.search.service;

import com.azure.search.documents.indexes.models.HnswAlgorithmConfiguration;
import com.azure.search.documents.indexes.models.LexicalAnalyzerName;
import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.indexes.models.SemanticConfiguration;
import com.azure.search.documents.indexes.models.SemanticField;
import com.azure.search.documents.indexes.models.SemanticPrioritizedFields;
import com.azure.search.documents.indexes.models.SemanticSearch;
import com.azure.search.documents.indexes.models.VectorSearch;
import com.azure.search.documents.indexes.models.VectorSearchProfile;
import com.intellidoc.config.IntelliDocProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SearchIndexDefinitionFactory {

    private final IntelliDocProperties properties;

    public SearchIndexDefinitionFactory(IntelliDocProperties properties) {
        this.properties = properties;
    }

    public SearchIndex buildChunkIndexDefinition() {
        IntelliDocProperties.Search search = properties.getSearch();

        SearchField id = new SearchField("id", SearchFieldDataType.STRING)
                .setKey(true)
                .setFilterable(false)
                .setSortable(false);
        SearchField documentId = new SearchField("document_id", SearchFieldDataType.STRING)
                .setFilterable(true)
                .setSortable(false);
        SearchField documentVersionId = new SearchField("document_version_id", SearchFieldDataType.STRING)
                .setFilterable(true)
                .setSortable(false);
        SearchField documentTitle = new SearchField("document_title", SearchFieldDataType.STRING)
                .setSearchable(true)
                .setAnalyzerName(LexicalAnalyzerName.EN_LUCENE);
        SearchField content = new SearchField("content", SearchFieldDataType.STRING)
                .setSearchable(true)
                .setAnalyzerName(LexicalAnalyzerName.EN_LUCENE);
        SearchField contentVector = new SearchField("content_vector", SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
                .setSearchable(true)
                .setVectorSearchDimensions(search.getVectorDimensions())
                .setVectorSearchProfileName(search.getVectorProfile());
        SearchField pageNumber = new SearchField("page_number", SearchFieldDataType.INT32)
                .setFilterable(true)
                .setSortable(true);
        SearchField sectionHeading = new SearchField("section_heading", SearchFieldDataType.STRING)
                .setSearchable(true)
                .setAnalyzerName(LexicalAnalyzerName.EN_LUCENE);
        SearchField chunkIndex = new SearchField("chunk_index", SearchFieldDataType.INT32)
                .setSortable(true)
                .setFilterable(true);
        SearchField category = new SearchField("category", SearchFieldDataType.STRING)
                .setFilterable(true)
                .setFacetable(true);
        SearchField department = new SearchField("department", SearchFieldDataType.STRING)
                .setFilterable(true)
                .setFacetable(true);
        SearchField effectiveDate = new SearchField("effective_date", SearchFieldDataType.DATE_TIME_OFFSET)
                .setFilterable(true)
                .setSortable(true);
        SearchField tags = new SearchField("tags", SearchFieldDataType.collection(SearchFieldDataType.STRING))
                .setFilterable(true)
                .setFacetable(true);
        SearchField allowedRoles = new SearchField("allowed_roles", SearchFieldDataType.collection(SearchFieldDataType.STRING))
                .setFilterable(true);
        SearchField allowedDepartments = new SearchField("allowed_departments", SearchFieldDataType.collection(SearchFieldDataType.STRING))
                .setFilterable(true);
        SearchField allowedUsers = new SearchField("allowed_users", SearchFieldDataType.collection(SearchFieldDataType.STRING))
                .setFilterable(true);
        SearchField status = new SearchField("status", SearchFieldDataType.STRING)
                .setFilterable(true)
                .setFacetable(true);

        SemanticPrioritizedFields prioritizedFields = new SemanticPrioritizedFields()
                .setTitleField(new SemanticField("document_title"))
                .setContentFields(List.of(
                        new SemanticField("content"),
                        new SemanticField("section_heading")))
                .setKeywordsFields(List.of(
                        new SemanticField("category"),
                        new SemanticField("department")));

        SemanticConfiguration semanticConfiguration = new SemanticConfiguration(
                search.getSemanticConfiguration(),
                prioritizedFields);

        VectorSearch vectorSearch = new VectorSearch()
                .setAlgorithms(List.of(new HnswAlgorithmConfiguration(search.getVectorAlgorithm())))
                .setProfiles(List.of(new VectorSearchProfile(search.getVectorProfile(), search.getVectorAlgorithm())));

        return new SearchIndex(search.getIndexName())
                .setFields(List.of(
                        id,
                        documentId,
                        documentVersionId,
                        documentTitle,
                        content,
                        contentVector,
                        pageNumber,
                        sectionHeading,
                        chunkIndex,
                        category,
                        department,
                        effectiveDate,
                        tags,
                        allowedRoles,
                        allowedDepartments,
                        allowedUsers,
                        status))
                .setVectorSearch(vectorSearch)
                .setSemanticSearch(new SemanticSearch().setConfigurations(List.of(semanticConfiguration)));
    }
}
