package com.intellidoc.chat.controller;

import com.intellidoc.chat.service.SavedAnswerService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class SavedAnswerController {

    private final SavedAnswerService savedAnswerService;

    public SavedAnswerController(SavedAnswerService savedAnswerService) {
        this.savedAnswerService = savedAnswerService;
    }

    @PostMapping("/messages/{messageId}/bookmark")
    public ResponseEntity<SavedAnswerService.SavedAnswerView> saveAnswer(
            @PathVariable UUID messageId,
            Authentication authentication) {
        return ResponseEntity.ok(savedAnswerService.saveAnswer(authentication.getName(), messageId));
    }

    @DeleteMapping("/messages/{messageId}/bookmark")
    public ResponseEntity<Void> removeSavedAnswer(
            @PathVariable UUID messageId,
            Authentication authentication) {
        savedAnswerService.removeSavedAnswer(authentication.getName(), messageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/saved-answers")
    public ResponseEntity<List<SavedAnswerService.SavedAnswerView>> listSavedAnswers(Authentication authentication) {
        return ResponseEntity.ok(savedAnswerService.listSavedAnswers(authentication.getName()));
    }
}
