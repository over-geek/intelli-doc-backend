package com.intellidoc.chat.service;

import com.intellidoc.chat.model.ChatMessageEntity;
import com.intellidoc.chat.model.ChatMessageRole;
import com.intellidoc.chat.model.SavedAnswerEntity;
import com.intellidoc.chat.repository.ChatMessageRepository;
import com.intellidoc.chat.repository.SavedAnswerRepository;
import com.intellidoc.shared.error.ConflictException;
import com.intellidoc.shared.error.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedAnswerService {

    private final ChatSessionService chatSessionService;
    private final ChatMessageRepository chatMessageRepository;
    private final SavedAnswerRepository savedAnswerRepository;

    public SavedAnswerService(
            ChatSessionService chatSessionService,
            ChatMessageRepository chatMessageRepository,
            SavedAnswerRepository savedAnswerRepository) {
        this.chatSessionService = chatSessionService;
        this.chatMessageRepository = chatMessageRepository;
        this.savedAnswerRepository = savedAnswerRepository;
    }

    @Transactional
    public SavedAnswerView saveAnswer(String userEmail, UUID messageId) {
        var user = chatSessionService.getCurrentUserEntity(userEmail);
        ChatMessageEntity message = getOwnedAssistantMessage(user.getId(), messageId);
        if (savedAnswerRepository.findByUser_IdAndMessage_Id(user.getId(), messageId).isPresent()) {
            throw new ConflictException("saved_answer_exists", "This answer has already been saved.");
        }
        SavedAnswerEntity saved = new SavedAnswerEntity();
        saved.setUser(user);
        saved.setMessage(message);
        SavedAnswerEntity persisted = savedAnswerRepository.save(saved);
        return toView(persisted);
    }

    @Transactional
    public void removeSavedAnswer(String userEmail, UUID messageId) {
        var user = chatSessionService.getCurrentUserEntity(userEmail);
        SavedAnswerEntity savedAnswer = savedAnswerRepository.findByUser_IdAndMessage_Id(user.getId(), messageId)
                .orElseThrow(() -> new NotFoundException(
                        "saved_answer_not_found",
                        "The requested saved answer does not exist for the current user."));
        savedAnswerRepository.delete(savedAnswer);
    }

    @Transactional(readOnly = true)
    public List<SavedAnswerView> listSavedAnswers(String userEmail) {
        var user = chatSessionService.getCurrentUserEntity(userEmail);
        return savedAnswerRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toView)
                .toList();
    }

    private ChatMessageEntity getOwnedAssistantMessage(UUID userId, UUID messageId) {
        ChatMessageEntity message = chatMessageRepository.findByIdAndSession_User_Id(messageId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "chat_message_not_found",
                        "The requested assistant message does not exist or is not accessible to the current user."));
        if (message.getRole() != ChatMessageRole.ASSISTANT) {
            throw new ConflictException("saved_answer_message_invalid", "Only assistant answers can be saved.");
        }
        return message;
    }

    private SavedAnswerView toView(SavedAnswerEntity savedAnswer) {
        ChatMessageEntity message = savedAnswer.getMessage();
        return new SavedAnswerView(
                savedAnswer.getId(),
                message.getId(),
                message.getSession().getId(),
                message.getContent(),
                message.getConfidenceScore(),
                message.getCreatedAt(),
                savedAnswer.getCreatedAt());
    }

    public record SavedAnswerView(
            UUID id,
            UUID messageId,
            UUID sessionId,
            String content,
            Double confidenceScore,
            Instant messageCreatedAt,
            Instant savedAt) {
    }
}
