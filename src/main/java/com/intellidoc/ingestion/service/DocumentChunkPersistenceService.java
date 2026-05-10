package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.ChunkCandidate;
import com.intellidoc.ingestion.model.ChunkingResult;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import com.intellidoc.ingestion.model.IngestionWorkItem;
import com.intellidoc.ingestion.repository.DocumentChunkRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentChunkPersistenceService {

    private final DocumentChunkRepository documentChunkRepository;

    public DocumentChunkPersistenceService(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    @Transactional
    public List<DocumentChunkEntity> replaceChunks(IngestionWorkItem workItem, ChunkingResult chunkingResult) {
        documentChunkRepository.deleteByDocumentVersionId(workItem.documentVersionId());

        List<DocumentChunkEntity> entities = new ArrayList<>();
        for (ChunkCandidate chunk : chunkingResult.chunks()) {
            DocumentChunkEntity entity = new DocumentChunkEntity();
            entity.setDocumentVersion(workItem.documentVersion());
            entity.setDocument(workItem.documentVersion().getDocument());
            entity.setChunkIndex(chunk.chunkIndex());
            entity.setContent(chunk.content());
            entity.setPageNumber(chunk.pageNumber());
            entity.setSectionHeading(chunk.sectionHeading());
            entity.setStartCharOffset(chunk.startCharOffset());
            entity.setEndCharOffset(chunk.endCharOffset());
            entity.setEmbeddingVector(List.of());
            entity.setAiSearchDocId(null);
            entities.add(entity);
        }

        return documentChunkRepository.saveAll(entities);
    }
}
