package com.intellidoc.ingestion.model;

import java.util.List;
import java.util.UUID;

public record ChunkEmbeddingResult(UUID chunkId, int chunkIndex, List<Float> vector) {
}
