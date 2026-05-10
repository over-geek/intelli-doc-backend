package com.intellidoc.ingestion.config;

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.intellidoc.config.IntelliDocProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class DocumentIntelligenceConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "intellidoc.ingestion.document-intelligence",
            name = "enabled",
            havingValue = "true")
    DocumentAnalysisClient documentAnalysisClient(IntelliDocProperties properties) {
        IntelliDocProperties.Ingestion.DocumentIntelligence config =
                properties.getIngestion().getDocumentIntelligence();

        if (!StringUtils.hasText(config.getEndpoint())) {
            throw new IllegalStateException(
                    "Document Intelligence is enabled but no endpoint is configured.");
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            throw new IllegalStateException(
                    "Document Intelligence is enabled but no API key is configured.");
        }
        if (!StringUtils.hasText(config.getModelId())) {
            throw new IllegalStateException(
                    "Document Intelligence is enabled but no model id is configured.");
        }

        return new DocumentAnalysisClientBuilder()
                .endpoint(config.getEndpoint().trim())
                .credential(new AzureKeyCredential(config.getApiKey().trim()))
                .buildClient();
    }
}
