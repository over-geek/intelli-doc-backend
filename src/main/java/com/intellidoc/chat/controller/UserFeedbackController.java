package com.intellidoc.chat.controller;

import com.intellidoc.chat.model.UserFeedbackRating;
import com.intellidoc.chat.service.UserFeedbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/chat/messages")
public class UserFeedbackController {

    private final UserFeedbackService userFeedbackService;

    public UserFeedbackController(UserFeedbackService userFeedbackService) {
        this.userFeedbackService = userFeedbackService;
    }

    @PostMapping("/{messageId}/feedback")
    public ResponseEntity<UserFeedbackService.FeedbackView> saveFeedback(
            @PathVariable UUID messageId,
            @Valid @RequestBody SaveFeedbackRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(userFeedbackService.saveFeedback(
                authentication.getName(),
                messageId,
                new UserFeedbackService.SaveFeedbackCommand(
                        request.rating(),
                        request.comment(),
                        request.flagged())));
    }

    public record SaveFeedbackRequest(
            @NotNull UserFeedbackRating rating,
            String comment,
            boolean flagged) {
    }
}
