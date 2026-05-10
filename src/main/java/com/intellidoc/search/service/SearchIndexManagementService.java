package com.intellidoc.search.service;

import com.azure.core.exception.HttpResponseException;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.intellidoc.config.IntelliDocProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SearchIndexManagementService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexManagementService.class);

    private final SearchIndexClient searchIndexClient;
    private final SearchIndexDefinitionFactory searchIndexDefinitionFactory;
    private final IntelliDocProperties properties;

    public SearchIndexManagementService(
            SearchIndexClient searchIndexClient,
            SearchIndexDefinitionFactory searchIndexDefinitionFactory,
            IntelliDocProperties properties) {
        this.searchIndexClient = searchIndexClient;
        this.searchIndexDefinitionFactory = searchIndexDefinitionFactory;
        this.properties = properties;
    }

    public SearchIndex ensureChunkIndex() {
        SearchIndex definition = searchIndexDefinitionFactory.buildChunkIndexDefinition();
        String indexName = properties.getSearch().getIndexName();

        try {
            SearchIndex existing = searchIndexClient.getIndex(indexName);
            definition.setETag(existing.getETag());
            SearchIndex updated = searchIndexClient.createOrUpdateIndex(definition);
            log.info("Updated Azure AI Search index '{}'.", indexName);
            return updated;
        } catch (HttpResponseException exception) {
            if (exception.getResponse() != null && exception.getResponse().getStatusCode() == 404) {
                SearchIndex created = searchIndexClient.createIndex(definition);
                log.info("Created Azure AI Search index '{}'.", indexName);
                return created;
            }
            throw exception;
        }
    }
}
