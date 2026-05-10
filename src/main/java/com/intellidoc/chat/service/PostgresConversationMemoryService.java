package com.intellidoc.chat.service;

import com.intellidoc.chat.model.ChatMessageEntity;
import com.intellidoc.chat.repository.ChatMessageRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostgresConversationMemoryService {

    private final ChatMessageRepository chatMessageRepository;

    public PostgresConversationMemoryService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public List<ConversationMemoryMessage> loadRecentMessages(UUID sessionId) {
        return chatMessageRepository.findTop5BySession_IdOrderByCreatedAtDesc(sessionId).stream()
                .sorted(Comparator.comparing(ChatMessageEntity::getCreatedAt))
                .map(message -> new ConversationMemoryMessage(
                        message.getId(),
                        message.getRole().name(),
                        message.getContent(),
                        message.getCreatedAt()))
                .toList();
    }

    public record ConversationMemoryMessage(UUID id, String role, String content, java.time.Instant createdAt) {
    }
}
