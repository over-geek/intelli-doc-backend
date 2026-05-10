package com.intellidoc.ingestion.config;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.ingestion.service.AzureOpenAiEmbeddingService;
import com.intellidoc.ingestion.service.EmbeddingService;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingServiceConfiguration {

    @Bean
    EmbeddingService embeddingService(
            OpenAIClientBuilder openAIClientBuilder,
            IntelliDocProperties properties) {
        AzureOpenAiEmbeddingOptions options = AzureOpenAiEmbeddingOptions.builder()
                .deploymentName(properties.getAi().getEmbeddingDeployment())
                .build();

        EmbeddingModel embeddingModel = new AzureOpenAiEmbeddingModel(
                openAIClientBuilder.buildClient(),
                MetadataMode.EMBED,
                options);

        return new AzureOpenAiEmbeddingService(embeddingModel, properties);
    }
}
