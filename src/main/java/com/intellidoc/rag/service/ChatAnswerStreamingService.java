package com.intellidoc.rag.service;

import com.azure.core.exception.HttpResponseException;
import com.intellidoc.chat.service.ChatSessionService;
import com.intellidoc.chat.service.ChatPersistenceService;
import com.intellidoc.chat.service.PostgresConversationMemoryService;
import com.intellidoc.search.service.HybridRetrievalService;
import com.intellidoc.security.model.AuthenticatedUser;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatAnswerStreamingService {

    private static final Logger log = LoggerFactory.getLogger(ChatAnswerStreamingService.class);

    private final HybridRetrievalService hybridRetrievalService;
    private final ContextAssemblyService contextAssemblyService;
    private final AnswerPromptManager answerPromptManager;
    private final CitationExtractorService citationExtractorService;
    private final ConfidenceScorerService confidenceScorerService;
    private final ChatSessionService chatSessionService;
    private final ChatPersistenceService chatPersistenceService;
    private final ChatModel chatModel;

    public ChatAnswerStreamingService(
            HybridRetrievalService hybridRetrievalService,
            ContextAssemblyService contextAssemblyService,
            AnswerPromptManager answerPromptManager,
            CitationExtractorService citationExtractorService,
            ConfidenceScorerService confidenceScorerService,
            ChatSessionService chatSessionService,
            ChatPersistenceService chatPersistenceService,
            ChatModel chatModel) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.contextAssemblyService = contextAssemblyService;
        this.answerPromptManager = answerPromptManager;
        this.citationExtractorService = citationExtractorService;
        this.confidenceScorerService = confidenceScorerService;
        this.chatSessionService = chatSessionService;
        this.chatPersistenceService = chatPersistenceService;
        this.chatModel = chatModel;
    }

    public SseEmitter streamAnswer(AuthenticatedUser user, UUID sessionId, String query) {
        SseEmitter emitter = new SseEmitter(0L);
        long startedAt = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            try {
                HybridRetrievalService.RetrievalResponse retrievalResponse =
                        hybridRetrievalService.retrieve(user, sessionId, query);
                ContextAssemblyService.AssembledContext assembledContext =
                        contextAssemblyService.assemble(retrievalResponse);
                List<PostgresConversationMemoryService.ConversationMemoryMessage> conversationMemory =
                        chatSessionService.loadConversationMemory(user.email(), sessionId);

                sendEvent(emitter, "retrieval", Map.of(
                        "originalQuery", retrievalResponse.originalQuery(),
                        "rewrittenQuery", retrievalResponse.rewrittenQuery(),
                        "rewritten", retrievalResponse.rewritten(),
                        "rewriteReason", retrievalResponse.rewriteReason(),
                        "resultCount", retrievalResponse.results().size()));

                Prompt prompt = answerPromptManager.buildPrompt(
                        retrievalResponse.rewrittenQuery(),
                        assembledContext,
                        conversationMemory);

                StringBuilder answerBuilder = new StringBuilder();
                chatModel.stream(prompt).doOnNext(response -> {
                    String delta = extractText(response);
                    if (delta == null || delta.isBlank()) {
                        return;
                    }
                    answerBuilder.append(delta);
                    sendEvent(emitter, "token", Map.of("text", delta));
                }).doOnError(error -> {
                    try {
                        sendEvent(emitter, "error", Map.of("message", buildClientErrorMessage(error)));
                        emitter.complete();
                    } catch (Exception ignored) {
                        log.warn("Unable to complete SSE emitter with error cleanly.", ignored);
                    }
                }).doOnComplete(() -> {
                    String answer = answerBuilder.toString().trim();
                    CitationExtractorService.CitationExtractionResult citations =
                            citationExtractorService.extract(answer, assembledContext);
                    ConfidenceScorerService.ConfidenceScore confidence =
                            confidenceScorerService.score(
                                    retrievalResponse.rewrittenQuery(),
                                    answer,
                                    assembledContext,
                                    citations);
                    ChatPersistenceService.PersistedAnswerResult persisted =
                            chatPersistenceService.persistAnswer(
                                    user.email(),
                                    sessionId,
                                    query,
                                    answer,
                                    citations,
                                    confidence,
                                    retrievalResponse.results().size(),
                                    System.currentTimeMillis() - startedAt);

                    Map<String, Object> completionPayload = new HashMap<>();
                    completionPayload.put("answer", answer);
                    completionPayload.put("citations", citations.citations());
                    completionPayload.put("confidence", confidence);
                    completionPayload.put("sources", assembledContext.sources());
                    completionPayload.put("estimatedContextTokens", assembledContext.estimatedTokens());
                    completionPayload.put("userMessageId", persisted.userMessageId());
                    completionPayload.put("assistantMessageId", persisted.assistantMessageId());
                    completionPayload.put("promptTokens", persisted.promptTokens());
                    completionPayload.put("completionTokens", persisted.completionTokens());
                    completionPayload.put("latencyMs", persisted.latencyMs());

                    sendEvent(emitter, "complete", completionPayload);
                    emitter.complete();
                }).blockLast();
            } catch (Exception exception) {
                log.error("Failed to stream chat answer for session {}", sessionId, exception);
                try {
                    sendEvent(
                            emitter,
                            "error",
                            Map.of("message", buildClientErrorMessage(exception)));
                } catch (Exception ignored) {
                    log.warn("Unable to send SSE error event.", ignored);
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send SSE event '%s'.".formatted(eventName), exception);
        }
    }

    private String buildClientErrorMessage(Throwable error) {
        if (error instanceof HttpResponseException httpResponseException) {
            int statusCode = httpResponseException.getResponse().getStatusCode();
            String message = httpResponseException.getMessage() == null ? "" : httpResponseException.getMessage();
            String normalized = message.toLowerCase();

            if (statusCode == 403 && normalized.contains("virtual network/firewall")) {
                return "The AI service denied this request because its firewall or virtual network rules do not currently allow this application to connect.";
            }

            if (statusCode == 403 && normalized.contains("public access is disabled")) {
                return "The AI service denied this request because public access is disabled and a private endpoint is required.";
            }
        }

        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }

        return "Chat answer generation failed.";
    }
}
