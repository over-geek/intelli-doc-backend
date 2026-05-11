package com.intellidoc.chat.service;

import com.intellidoc.chat.model.ChatMessageEntity;
import com.intellidoc.chat.model.ChatMessageRole;
import com.intellidoc.chat.model.ChatSessionEntity;
import com.intellidoc.chat.model.MessageSourceEntity;
import com.intellidoc.chat.repository.ChatMessageRepository;
import com.intellidoc.chat.repository.MessageSourceRepository;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import com.intellidoc.admin.model.DocumentEntity;
import com.intellidoc.rag.service.CitationExtractorService;
import com.intellidoc.rag.service.ConfidenceScorerService;
import com.intellidoc.shared.error.BadRequestException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);
    private static final String DEFAULT_SESSION_TITLE = "New conversation";

    private final ChatSessionService chatSessionService;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final TokenUsageEstimatorService tokenUsageEstimatorService;
    private final EntityManager entityManager;

    public ChatPersistenceService(
            ChatSessionService chatSessionService,
            ChatMessageRepository chatMessageRepository,
            MessageSourceRepository messageSourceRepository,
            TokenUsageEstimatorService tokenUsageEstimatorService,
            EntityManager entityManager) {
        this.chatSessionService = chatSessionService;
        this.chatMessageRepository = chatMessageRepository;
        this.messageSourceRepository = messageSourceRepository;
        this.tokenUsageEstimatorService = tokenUsageEstimatorService;
        this.entityManager = entityManager;
    }

    @Transactional
    public PersistedAnswerResult persistAnswer(
            String userEmail,
            UUID sessionId,
            String userQuery,
            String answer,
            CitationExtractorService.CitationExtractionResult citationExtractionResult,
            ConfidenceScorerService.ConfidenceScore confidenceScore,
            int retrievalCount,
            long latencyMs) {
        if (!StringUtils.hasText(userQuery)) {
            throw new BadRequestException("chat_user_query_required", "A user query is required for persistence.");
        }
        if (!StringUtils.hasText(answer)) {
            throw new BadRequestException("chat_answer_required", "An assistant answer is required for persistence.");
        }

        ChatSessionEntity session = chatSessionService.getOwnedSessionEntity(userEmail, sessionId);

        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setSession(session);
        userMessage.setRole(ChatMessageRole.USER);
        userMessage.setContent(userQuery.trim());
        userMessage.setCitations(List.of());
        userMessage.setTokenCountPrompt(0);
        userMessage.setTokenCountCompletion(0);
        userMessage.setRetrievalCount(0);
        userMessage.setLatencyMs(0);
        ChatMessageEntity savedUserMessage = chatMessageRepository.save(userMessage);

        ChatMessageEntity assistantMessage = new ChatMessageEntity();
        assistantMessage.setSession(session);
        assistantMessage.setRole(ChatMessageRole.ASSISTANT);
        assistantMessage.setContent(answer.trim());
        assistantMessage.setCitations(citationExtractionResult.citations().stream()
                .map(CitationExtractorService.CitationView::marker)
                .toList());
        assistantMessage.setConfidenceScore(confidenceScore.score());
        assistantMessage.setTokenCountPrompt(tokenUsageEstimatorService.estimate(userQuery));
        assistantMessage.setTokenCountCompletion(tokenUsageEstimatorService.estimate(answer));
        assistantMessage.setRetrievalCount(retrievalCount);
        assistantMessage.setLatencyMs(latencyMs);
        ChatMessageEntity savedAssistantMessage = chatMessageRepository.save(assistantMessage);

        List<MessageSourceEntity> sources = citationExtractionResult.citations().stream()
                .map(citation -> {
                    MessageSourceEntity source = new MessageSourceEntity();
                    source.setMessage(savedAssistantMessage);
                    DocumentChunkEntity chunk =
                            entityManager.getReference(DocumentChunkEntity.class, UUID.fromString(citation.chunkId()));
                    source.setChunk(chunk);
                    DocumentEntity document =
                            entityManager.getReference(DocumentEntity.class, UUID.fromString(citation.documentId()));
                    source.setDocument(document);
                    source.setDocumentTitle(citation.documentTitle());
                    source.setPageNumber(citation.pageNumber());
                    source.setSectionHeading(citation.sectionHeading());
                    source.setExcerpt(citation.excerpt());
                    source.setRelevanceScore(citation.rerankerScore() != null
                            ? citation.rerankerScore()
                            : citation.searchScore());
                    source.setDisplayOrder(citation.displayOrder());
                    return source;
                })
                .toList();

        messageSourceRepository.saveAll(sources);

        if (DEFAULT_SESSION_TITLE.equals(session.getTitle())) {
            session.setTitle(deriveSessionTitle(userQuery));
        }
        session.setMessageCount(session.getMessageCount() + 2);
        session.setLastMessageAt(savedAssistantMessage.getCreatedAt());

        log.info(
                "Persisted chat exchange for session {} (userMessageId={}, assistantMessageId={}, sources={}).",
                sessionId,
                savedUserMessage.getId(),
                savedAssistantMessage.getId(),
                sources.size());

        return new PersistedAnswerResult(
                savedUserMessage.getId(),
                savedAssistantMessage.getId(),
                savedAssistantMessage.getConfidenceScore(),
                savedAssistantMessage.getTokenCountPrompt(),
                savedAssistantMessage.getTokenCountCompletion(),
                savedAssistantMessage.getRetrievalCount(),
                savedAssistantMessage.getLatencyMs());
    }

    public record PersistedAnswerResult(
            UUID userMessageId,
            UUID assistantMessageId,
            Double confidenceScore,
            int promptTokens,
            int completionTokens,
            int retrievalCount,
            long latencyMs) {
    }

    private String deriveSessionTitle(String userQuery) {
        String normalized = userQuery == null ? "" : userQuery.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return DEFAULT_SESSION_TITLE;
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 77).trim() + "...";
    }
}
