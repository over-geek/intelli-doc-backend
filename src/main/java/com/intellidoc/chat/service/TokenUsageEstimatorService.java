package com.intellidoc.chat.service;

import org.springframework.stereotype.Service;

@Service
public class TokenUsageEstimatorService {

    private static final int APPROX_CHARS_PER_TOKEN = 4;

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, text.length() / APPROX_CHARS_PER_TOKEN);
    }
}
