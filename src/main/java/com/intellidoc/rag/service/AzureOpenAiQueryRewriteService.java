package com.intellidoc.rag.service;

import com.intellidoc.chat.service.ChatSessionService;
import com.intellidoc.chat.service.PostgresConversationMemoryService;
import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.shared.error.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

public class AzureOpenAiQueryRewriteService implements QueryRewriteService {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiQueryRewriteService.class);

    private static final String REWRITE_SYSTEM_PROMPT = """
            You rewrite follow-up policy questions into standalone search queries.

            Rules:
            1. Return only the rewritten standalone query.
            2. Do not answer the question.
            3. If the latest question is already standalone, return it unchanged.
            4. If the conversation does not provide enough context, return the latest question unchanged.
            5. Preserve policy-specific terminology, names, dates, and acronyms when present.
            6. Do not add quotation marks, bullet points, labels, or explanations.
            """;

    private final ChatModel chatModel;
    private final ChatSessionService chatSessionService;
    private final IntelliDocProperties properties;

    public AzureOpenAiQueryRewriteService(
            ChatModel chatModel,
            ChatSessionService chatSessionService,
            IntelliDocProperties properties) {
        this.chatModel = chatModel;
        this.chatSessionService = chatSessionService;
        this.properties = properties;
    }

    @Override
    public QueryRewriteResult rewriteQuery(String userEmail, UUID sessionId, String query) {
        String normalizedQuery = normalizeQuery(query);
        List<PostgresConversationMemoryService.ConversationMemoryMessage> memoryWindow =
                chatSessionService.loadConversationMemory(userEmail, sessionId);

        if (!properties.getChat().isQueryRewriteEnabled()) {
            return new QueryRewriteResult(normalizedQuery, normalizedQuery, false, false, "disabled");
        }

        if (memoryWindow.isEmpty()) {
            return new QueryRewriteResult(normalizedQuery, normalizedQuery, false, false, "no_history");
        }

        Prompt prompt = new Prompt(buildPromptMessages(memoryWindow, normalizedQuery));
        String rewritten = sanitize(chatModel.call(prompt).getResult().getOutput().getText(), normalizedQuery);
        boolean changed = !normalizedQuery.equals(rewritten);

        log.info(
                "Rewrote chat query for session {} (changed={}, memoryMessages={}).",
                sessionId,
                changed,
                memoryWindow.size());

        return new QueryRewriteResult(
                normalizedQuery,
                rewritten,
                changed,
                true,
                changed ? "rewritten" : "unchanged");
    }

    private List<Message> buildPromptMessages(
            List<PostgresConversationMemoryService.ConversationMemoryMessage> memoryWindow,
            String latestQuestion) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(REWRITE_SYSTEM_PROMPT));

        StringBuilder history = new StringBuilder();
        for (PostgresConversationMemoryService.ConversationMemoryMessage memoryMessage : memoryWindow) {
            history.append(memoryMessage.role())
                    .append(": ")
                    .append(memoryMessage.content())
                    .append(System.lineSeparator());
        }

        messages.add(new UserMessage("""
                Conversation history:
                %s

                Latest question:
                %s

                Rewrite the latest question as a standalone search query.
                """.formatted(history.toString().trim(), latestQuestion)));
        return messages;
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? null : query.trim();
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("query_rewrite_query_required", "A query is required for rewriting.");
        }
        return normalized;
    }

    private String sanitize(String rewritten, String fallback) {
        if (rewritten == null) {
            return fallback;
        }
        String normalized = rewritten.trim();
        if (normalized.isBlank()) {
            return fallback;
        }
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        normalized = normalized.replace("\r", " ").replace("\n", " ").trim();
        return normalized.isBlank() ? fallback : normalized;
    }
}
