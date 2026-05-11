package com.intellidoc.rag.config;

import com.intellidoc.chat.service.ChatSessionService;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.rag.service.AzureOpenAiQueryRewriteService;
import com.intellidoc.rag.service.NoOpQueryRewriteService;
import com.intellidoc.rag.service.QueryRewriteService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryRewriteConfiguration {

    @Bean
    @ConditionalOnBean(ChatModel.class)
    QueryRewriteService queryRewriteService(
            ChatModel chatModel,
            ChatSessionService chatSessionService,
            IntelliDocProperties properties) {
        return new AzureOpenAiQueryRewriteService(chatModel, chatSessionService, properties);
    }

    @Bean
    @ConditionalOnMissingBean(QueryRewriteService.class)
    QueryRewriteService noOpQueryRewriteService() {
        return new NoOpQueryRewriteService();
    }
}
