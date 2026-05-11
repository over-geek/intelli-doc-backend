package com.intellidoc.rag.controller;

import com.intellidoc.rag.service.ChatAnswerStreamingService;
import com.intellidoc.security.jwt.EntraJwtClaimsMapper;
import com.intellidoc.security.model.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/chat/sessions")
public class ChatAnswerController {

    private final ChatAnswerStreamingService chatAnswerStreamingService;
    private final EntraJwtClaimsMapper claimsMapper;

    public ChatAnswerController(
            ChatAnswerStreamingService chatAnswerStreamingService,
            EntraJwtClaimsMapper claimsMapper) {
        this.chatAnswerStreamingService = chatAnswerStreamingService;
        this.claimsMapper = claimsMapper;
    }

    @PostMapping(path = "/{sessionId}/stream-answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnswer(
            @PathVariable UUID sessionId,
            @Valid @RequestBody StreamAnswerRequest request,
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser user = claimsMapper.toAuthenticatedUser(jwt, authentication.getAuthorities());
        return chatAnswerStreamingService.streamAnswer(user, sessionId, request.query());
    }

    public record StreamAnswerRequest(@NotBlank String query) {
    }
}
