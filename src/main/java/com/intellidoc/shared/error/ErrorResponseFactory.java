package com.intellidoc.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ErrorResponseFactory {

    public ApiErrorResponse build(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<ApiValidationError> validationErrors) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                Optional.ofNullable(MDC.get("traceId")).orElse(null),
                validationErrors == null ? List.of() : List.copyOf(validationErrors));
    }
}
