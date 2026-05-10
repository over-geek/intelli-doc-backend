package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.ChunkEmbeddingResult;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import com.intellidoc.ingestion.repository.DocumentChunkRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChunkEmbeddingPersistenceService {

    private final DocumentChunkRepository documentChunkRepository;

    public ChunkEmbeddingPersistenceService(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    @Transactional
    public void applyEmbeddings(UUID documentVersionId, List<ChunkEmbeddingResult> embeddings) {
        if (embeddings.isEmpty()) {
            return;
        }

        Map<UUID, ChunkEmbeddingResult> resultsByChunkId = embeddings.stream()
                .collect(Collectors.toMap(ChunkEmbeddingResult::chunkId, Function.identity()));

        List<DocumentChunkEntity> chunks = documentChunkRepository.findByDocumentVersionIdOrderByChunkIndexAsc(documentVersionId);
        for (DocumentChunkEntity chunk : chunks) {
            ChunkEmbeddingResult result = resultsByChunkId.get(chunk.getId());
            if (result != null) {
                chunk.setEmbeddingVector(result.vector());
            }
        }
        documentChunkRepository.saveAll(chunks);
    }
}
