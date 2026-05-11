package com.intellidoc.search.service;

import com.intellidoc.shared.error.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QueryEmbeddingService {

    private final EmbeddingModel embeddingModel;

    public QueryEmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<Float> embedQuery(String query) {
        String normalized = normalize(query);
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(normalized), null));
        return toList(response);
    }

    public List<Float> embedText(String text) {
        String normalized = normalize(text);
        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(List.of(normalized), null));
        return toList(response);
    }

    private String normalize(String input) {
        String normalized = input == null ? null : input.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BadRequestException("search_query_required", "A search query is required.");
        }
        return normalized;
    }

    private List<Float> toList(EmbeddingResponse response) {
        if (response.getResults().isEmpty() || response.getResults().getFirst().getOutput() == null) {
            throw new IllegalStateException("Azure OpenAI did not return an embedding vector.");
        }

        float[] vector = response.getResults().getFirst().getOutput();
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
