package com.intellidoc.ingestion.service;

import com.intellidoc.config.IntelliDocProperties;
import com.intellidoc.ingestion.model.ChunkEmbeddingResult;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public class AzureOpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiEmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final IntelliDocProperties properties;

    public AzureOpenAiEmbeddingService(EmbeddingModel embeddingModel, IntelliDocProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public List<ChunkEmbeddingResult> embed(List<DocumentChunkEntity> chunks) {
        if (chunks.isEmpty()) {
            return List.of();
        }

        IntelliDocProperties.Ingestion.Embedding embeddingConfig = properties.getIngestion().getEmbedding();
        if (!embeddingConfig.isEnabled()) {
            log.info("Embedding generation is disabled for this environment.");
            return List.of();
        }

        List<ChunkEmbeddingResult> results = new ArrayList<>();
        int batchSize = embeddingConfig.getBatchSize();
        for (int start = 0; start < chunks.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunks.size());
            List<DocumentChunkEntity> batch = chunks.subList(start, end);
            results.addAll(embedBatchWithRetry(batch, embeddingConfig.getMaxRetries()));
        }

        log.info("Generated embeddings for {} chunks using Azure OpenAI.", results.size());
        return results;
    }

    private List<ChunkEmbeddingResult> embedBatchWithRetry(List<DocumentChunkEntity> batch, int maxRetries) {
        int attempt = 0;
        while (true) {
            try {
                return embedBatch(batch);
            } catch (RuntimeException exception) {
                if (attempt >= maxRetries) {
                    throw exception;
                }
                attempt++;
                Duration delay = Duration.ofSeconds((long) Math.pow(2, attempt - 1));
                log.warn(
                        "Embedding batch failed on attempt {} of {}. Retrying in {} ms.",
                        attempt,
                        maxRetries + 1,
                        delay.toMillis(),
                        exception);
                sleep(delay);
            }
        }
    }

    private List<ChunkEmbeddingResult> embedBatch(List<DocumentChunkEntity> batch) {
        List<String> inputs = batch.stream()
                .map(DocumentChunkEntity::getContent)
                .toList();

        EmbeddingResponse response = embeddingModel.call(new EmbeddingRequest(inputs, null));
        List<float[]> vectors = response.getResults().stream()
                .map(result -> result.getOutput())
                .toList();

        if (vectors.size() != batch.size()) {
            throw new IllegalStateException(
                    "Embedding response size mismatch. Expected %s vectors but received %s."
                            .formatted(batch.size(), vectors.size()));
        }

        return IntStream.range(0, batch.size())
                .mapToObj(index -> {
                    DocumentChunkEntity chunk = batch.get(index);
                    return new ChunkEmbeddingResult(
                            chunk.getId(),
                            chunk.getChunkIndex(),
                            toList(vectors.get(index)));
                })
                .toList();
    }

    private List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding retry was interrupted.", exception);
        }
    }
}
