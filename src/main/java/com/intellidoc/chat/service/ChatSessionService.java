package com.intellidoc.chat.service;

import com.intellidoc.chat.model.ChatMessageEntity;
import com.intellidoc.chat.model.ChatSessionEntity;
import com.intellidoc.chat.repository.MessageSourceRepository;
import com.intellidoc.chat.repository.SavedAnswerRepository;
import com.intellidoc.chat.repository.UserFeedbackRepository;
import com.intellidoc.chat.repository.ChatMessageRepository;
import com.intellidoc.chat.repository.ChatSessionRepository;
import com.intellidoc.security.model.AppUserEntity;
import com.intellidoc.security.repository.AppUserRepository;
import com.intellidoc.shared.error.BadRequestException;
import com.intellidoc.shared.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);
    private static final String DEFAULT_SESSION_TITLE = "New conversation";

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final SavedAnswerRepository savedAnswerRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final AppUserRepository appUserRepository;
    private final PostgresConversationMemoryService conversationMemoryService;

    public ChatSessionService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            MessageSourceRepository messageSourceRepository,
            SavedAnswerRepository savedAnswerRepository,
            UserFeedbackRepository userFeedbackRepository,
            AppUserRepository appUserRepository,
            PostgresConversationMemoryService conversationMemoryService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messageSourceRepository = messageSourceRepository;
        this.savedAnswerRepository = savedAnswerRepository;
        this.userFeedbackRepository = userFeedbackRepository;
        this.appUserRepository = appUserRepository;
        this.conversationMemoryService = conversationMemoryService;
    }

    @Transactional
    public ChatSessionView createSession(String userEmail, CreateSessionCommand command) {
        AppUserEntity user = getCurrentUser(userEmail);
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUser(user);
        session.setTitle(resolveTitle(command.title()));
        session.setActive(true);
        session.setMessageCount(0);
        session.setLastMessageAt(Instant.now());
        ChatSessionEntity saved = chatSessionRepository.save(session);
        log.info("Created chat session {} for user {}", saved.getId(), user.getEmail());
        return toSessionView(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionView> listSessions(String userEmail) {
        AppUserEntity user = getCurrentUser(userEmail);
        return chatSessionRepository.findByUser_IdOrderByLastMessageAtDesc(user.getId()).stream()
                .map(this::toSessionView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSessionDetailView getSessionMessages(String userEmail, UUID sessionId) {
        AppUserEntity user = getCurrentUser(userEmail);
        ChatSessionEntity session = getOwnedSession(userEmail, sessionId);
        List<ChatMessageView> messages = chatMessageRepository.findBySession_IdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toMessageView)
                .toList();
        List<PostgresConversationMemoryService.ConversationMemoryMessage> memoryWindow =
                conversationMemoryService.loadRecentMessages(sessionId);
        return new ChatSessionDetailView(
                toSessionView(session),
                messages,
                memoryWindow,
                savedAnswerRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                        .map(savedAnswer -> savedAnswer.getMessage().getId())
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<PostgresConversationMemoryService.ConversationMemoryMessage> loadConversationMemory(
            String userEmail, UUID sessionId) {
        getOwnedSession(userEmail, sessionId);
        return conversationMemoryService.loadRecentMessages(sessionId);
    }

    @Transactional
    public void deleteSession(String userEmail, UUID sessionId) {
        ChatSessionEntity session = getOwnedSession(userEmail, sessionId);
        messageSourceRepository.deleteBySessionId(sessionId);
        savedAnswerRepository.deleteBySessionId(sessionId);
        userFeedbackRepository.deleteBySessionId(sessionId);
        chatMessageRepository.deleteBySession_Id(sessionId);
        chatSessionRepository.delete(session);
        log.info("Deleted chat session {} for user {}", sessionId, session.getUser().getEmail());
    }

    @Transactional(readOnly = true)
    public AppUserEntity getCurrentUserEntity(String userEmail) {
        return getCurrentUser(userEmail);
    }

    @Transactional(readOnly = true)
    public ChatSessionEntity getOwnedSessionEntity(String userEmail, UUID sessionId) {
        return getOwnedSession(userEmail, sessionId);
    }

    private AppUserEntity getCurrentUser(String userEmail) {
        if (!StringUtils.hasText(userEmail)) {
            throw new BadRequestException("chat_user_missing", "An authenticated user email is required.");
        }
        return appUserRepository.findByEmailIgnoreCase(userEmail.trim())
                .orElseThrow(() -> new NotFoundException(
                        "chat_user_not_found",
                        "The authenticated user could not be resolved in the application database."));
    }

    private ChatSessionEntity getOwnedSession(String userEmail, UUID sessionId) {
        AppUserEntity user = getCurrentUser(userEmail);
        return chatSessionRepository.findByIdAndUser_Id(sessionId, user.getId())
                .orElseThrow(() -> new NotFoundException(
                        "chat_session_not_found",
                        "The requested chat session does not exist or is not accessible to the current user."));
    }

    private String resolveTitle(String title) {
        String normalized = title == null ? null : title.trim();
        if (!StringUtils.hasText(normalized)) {
            return DEFAULT_SESSION_TITLE;
        }
        if (normalized.length() > 255) {
            throw new BadRequestException(
                    "chat_session_title_too_long",
                    "Chat session titles must be 255 characters or fewer.");
        }
        return normalized;
    }

    private ChatSessionView toSessionView(ChatSessionEntity session) {
        return new ChatSessionView(
                session.getId(),
                session.getTitle(),
                session.isActive(),
                session.getMessageCount(),
                session.getCreatedAt(),
                session.getLastMessageAt());
    }

    private ChatMessageView toMessageView(ChatMessageEntity message) {
        List<MessageSourceView> sources = messageSourceRepository.findByMessage_IdOrderByDisplayOrderAsc(message.getId()).stream()
                .map(source -> new MessageSourceView(
                        source.getId(),
                        source.getChunk().getId(),
                        source.getDocument().getId(),
                        source.getDocumentTitle(),
                        source.getPageNumber(),
                        source.getSectionHeading(),
                        source.getExcerpt(),
                        source.getRelevanceScore(),
                        source.getDisplayOrder()))
                .toList();
        FeedbackStateView feedback = userFeedbackRepository.findByUser_IdAndMessage_Id(
                        message.getSession().getUser().getId(),
                        message.getId())
                .map(savedFeedback -> new FeedbackStateView(
                        savedFeedback.getRating().name(),
                        savedFeedback.getComment(),
                        savedFeedback.isFlagged(),
                        savedFeedback.getCreatedAt()))
                .orElse(null);
        return new ChatMessageView(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                List.copyOf(message.getCitations()),
                sources,
                feedback,
                message.getConfidenceScore(),
                message.getTokenCountPrompt(),
                message.getTokenCountCompletion(),
                message.getRetrievalCount(),
                message.getLatencyMs(),
                message.getCreatedAt());
    }

    public record CreateSessionCommand(String title) {
    }

    public record ChatSessionView(
            UUID id,
            String title,
            boolean active,
            int messageCount,
            Instant createdAt,
            Instant lastMessageAt) {
    }

    public record ChatMessageView(
            UUID id,
            String role,
            String content,
            List<String> citations,
            List<MessageSourceView> sources,
            FeedbackStateView feedback,
            Double confidenceScore,
            int tokenCountPrompt,
            int tokenCountCompletion,
            int retrievalCount,
            long latencyMs,
            Instant createdAt) {
    }

    public record ChatSessionDetailView(
            ChatSessionView session,
            List<ChatMessageView> messages,
            List<PostgresConversationMemoryService.ConversationMemoryMessage> memoryWindow,
            List<UUID> savedAssistantMessageIds) {
    }

    public record MessageSourceView(
            UUID id,
            UUID chunkId,
            UUID documentId,
            String documentTitle,
            Integer pageNumber,
            String sectionHeading,
            String excerpt,
            Double relevanceScore,
            int displayOrder) {
    }

    public record FeedbackStateView(
            String rating,
            String comment,
            boolean flagged,
            Instant createdAt) {
    }
}
