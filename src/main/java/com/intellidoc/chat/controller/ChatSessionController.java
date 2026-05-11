package com.intellidoc.chat.controller;

import com.intellidoc.chat.service.ChatSessionService;
import com.intellidoc.chat.service.PostgresConversationMemoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/chat/sessions")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    public ChatSessionController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public ResponseEntity<ChatSessionService.ChatSessionView> createSession(
            @Valid @RequestBody(required = false) CreateChatSessionRequest request,
            Authentication authentication) {
        ChatSessionService.ChatSessionView created = chatSessionService.createSession(
                authentication.getName(),
                new ChatSessionService.CreateSessionCommand(request == null ? null : request.title()));
        return ResponseEntity.created(URI.create("/api/chat/sessions/" + created.id())).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ChatSessionService.ChatSessionView>> listSessions(Authentication authentication) {
        return ResponseEntity.ok(chatSessionService.listSessions(authentication.getName()));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ChatSessionService.ChatSessionDetailView> getSessionMessages(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(chatSessionService.getSessionMessages(authentication.getName(), sessionId));
    }

    @GetMapping("/{sessionId}/memory")
    public ResponseEntity<List<PostgresConversationMemoryService.ConversationMemoryMessage>> getConversationMemory(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        return ResponseEntity.ok(chatSessionService.loadConversationMemory(authentication.getName(), sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId, Authentication authentication) {
        chatSessionService.deleteSession(authentication.getName(), sessionId);
        return ResponseEntity.noContent().build();
    }

    public record CreateChatSessionRequest(@Size(max = 255) String title) {
    }
}
