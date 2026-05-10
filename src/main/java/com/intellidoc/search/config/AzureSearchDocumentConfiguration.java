package com.intellidoc.search.config;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClient;
import com.azure.search.documents.SearchClientBuilder;
import com.intellidoc.config.IntelliDocProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AzureSearchDocumentConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "intellidoc.search", name = "enabled", havingValue = "true", matchIfMissing = true)
    SearchClient searchDocumentClient(
            @Value("${spring.ai.vectorstore.azure.url:}") String endpoint,
            @Value("${spring.ai.vectorstore.azure.api-key:}") String apiKey,
            IntelliDocProperties properties) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("Azure AI Search endpoint is required for document indexing.");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Azure AI Search API key is required for document indexing.");
        }

        return new SearchClientBuilder()
                .endpoint(endpoint.trim())
                .credential(new AzureKeyCredential(apiKey.trim()))
                .indexName(properties.getSearch().getIndexName())
                .buildClient();
    }
}
