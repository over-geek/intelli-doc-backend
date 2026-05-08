package com.intellidoc.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_MDC_KEY = "traceId";
    private static final String TRACEPARENT_HEADER = "traceparent";
    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = extractTraceId(request);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(CORRELATION_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String extractTraceId(HttpServletRequest request) {
        String traceparent = request.getHeader(TRACEPARENT_HEADER);
        if (StringUtils.hasText(traceparent)) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 4 && parts[1].length() == 32) {
                return parts[1];
            }
        }

        String correlationId = request.getHeader(CORRELATION_HEADER);
        if (StringUtils.hasText(correlationId)) {
            return correlationId.trim();
        }

        return UUID.randomUUID().toString().replace("-", "");
    }
}
