package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.ChunkEmbeddingResult;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import java.util.List;

public interface EmbeddingService {

    List<ChunkEmbeddingResult> embed(List<DocumentChunkEntity> chunks);
}
