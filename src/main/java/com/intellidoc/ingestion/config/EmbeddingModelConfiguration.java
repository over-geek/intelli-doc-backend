package com.intellidoc.ingestion.config;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class EmbeddingModelConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenAIClientBuilder.class)
    OpenAIClientBuilder azureOpenAiClientBuilder(
            @Value("${spring.ai.azure.openai.endpoint:}") String endpoint,
            @Value("${spring.ai.azure.openai.api-key:}") String apiKey) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("Azure OpenAI endpoint is required to build the embedding client.");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Azure OpenAI API key is required to build the embedding client.");
        }

        return new OpenAIClientBuilder()
                .endpoint(endpoint.trim())
                .credential(new AzureKeyCredential(apiKey.trim()));
    }
}
