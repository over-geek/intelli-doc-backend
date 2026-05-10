package com.intellidoc.search.config;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AzureSearchConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "intellidoc.search", name = "enabled", havingValue = "true", matchIfMissing = true)
    SearchIndexClient searchIndexClient(
            @Value("${spring.ai.vectorstore.azure.url:}") String endpoint,
            @Value("${spring.ai.vectorstore.azure.api-key:}") String apiKey) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("Azure AI Search endpoint is required for index management.");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Azure AI Search API key is required for index management.");
        }

        return new SearchIndexClientBuilder()
                .endpoint(endpoint.trim())
                .credential(new AzureKeyCredential(apiKey.trim()))
                .buildClient();
    }
}
