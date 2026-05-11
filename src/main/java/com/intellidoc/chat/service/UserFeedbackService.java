package com.intellidoc.chat.service;

import com.intellidoc.chat.model.ChatMessageEntity;
import com.intellidoc.chat.model.ChatMessageRole;
import com.intellidoc.chat.model.UserFeedbackEntity;
import com.intellidoc.chat.model.UserFeedbackRating;
import com.intellidoc.chat.repository.ChatMessageRepository;
import com.intellidoc.chat.repository.UserFeedbackRepository;
import com.intellidoc.shared.error.ConflictException;
import com.intellidoc.shared.error.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserFeedbackService {

    private final ChatSessionService chatSessionService;
    private final ChatMessageRepository chatMessageRepository;
    private final UserFeedbackRepository userFeedbackRepository;

    public UserFeedbackService(
            ChatSessionService chatSessionService,
            ChatMessageRepository chatMessageRepository,
            UserFeedbackRepository userFeedbackRepository) {
        this.chatSessionService = chatSessionService;
        this.chatMessageRepository = chatMessageRepository;
        this.userFeedbackRepository = userFeedbackRepository;
    }

    @Transactional
    public FeedbackView saveFeedback(String userEmail, UUID messageId, SaveFeedbackCommand command) {
        var user = chatSessionService.getCurrentUserEntity(userEmail);
        ChatMessageEntity message = getOwnedAssistantMessage(user.getId(), messageId);

        UserFeedbackEntity feedback = userFeedbackRepository.findByUser_IdAndMessage_Id(user.getId(), messageId)
                .orElseGet(UserFeedbackEntity::new);
        feedback.setUser(user);
        feedback.setMessage(message);
        feedback.setRating(command.rating());
        feedback.setComment(command.comment() == null ? null : command.comment().trim());
        feedback.setFlagged(command.flagged());
        UserFeedbackEntity saved = userFeedbackRepository.save(feedback);
        return toView(saved);
    }

    private ChatMessageEntity getOwnedAssistantMessage(UUID userId, UUID messageId) {
        ChatMessageEntity message = chatMessageRepository.findByIdAndSession_User_Id(messageId, userId)
                .orElseThrow(() -> new NotFoundException(
                        "chat_message_not_found",
                        "The requested assistant message does not exist or is not accessible to the current user."));
        if (message.getRole() != ChatMessageRole.ASSISTANT) {
            throw new ConflictException("feedback_message_invalid", "Feedback can only be left on assistant answers.");
        }
        return message;
    }

    private FeedbackView toView(UserFeedbackEntity feedback) {
        return new FeedbackView(
                feedback.getId(),
                feedback.getMessage().getId(),
                feedback.getRating(),
                feedback.getComment(),
                feedback.isFlagged(),
                feedback.getCreatedAt());
    }

    public record SaveFeedbackCommand(UserFeedbackRating rating, String comment, boolean flagged) {
    }

    public record FeedbackView(
            UUID id,
            UUID messageId,
            UserFeedbackRating rating,
            String comment,
            boolean flagged,
            Instant createdAt) {
    }
}
