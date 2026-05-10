package com.intellidoc.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellidoc.shared.error.ApiErrorResponse;
import com.intellidoc.shared.error.ErrorResponseFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseFactory errorResponseFactory;
    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ErrorResponseFactory errorResponseFactory, ObjectMapper objectMapper) {
        this.errorResponseFactory = errorResponseFactory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        ApiErrorResponse body = errorResponseFactory.build(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "You do not have permission to perform this action.",
                request,
                List.of());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
