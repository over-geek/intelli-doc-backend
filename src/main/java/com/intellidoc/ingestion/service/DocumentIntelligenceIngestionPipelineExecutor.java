package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.IngestionWorkItem;
import com.intellidoc.ingestion.model.ParsedDocumentLayout;
import com.intellidoc.ingestion.model.ChunkingResult;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import com.intellidoc.ingestion.model.ChunkEmbeddingResult;
import com.intellidoc.search.service.SearchIndexUpsertService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(DocumentAnalysisClient.class)
public class DocumentIntelligenceIngestionPipelineExecutor implements IngestionPipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntelligenceIngestionPipelineExecutor.class);

    private final DocumentLayoutExtractionService documentLayoutExtractionService;
    private final ChunkingService chunkingService;
    private final DocumentChunkPersistenceService documentChunkPersistenceService;
    private final EmbeddingService embeddingService;
    private final ChunkEmbeddingPersistenceService chunkEmbeddingPersistenceService;
    private final SearchIndexUpsertService searchIndexUpsertService;
    private final IngestionProcessingStateService ingestionProcessingStateService;
    private final IngestionMetricsService ingestionMetricsService;

    public DocumentIntelligenceIngestionPipelineExecutor(
            DocumentLayoutExtractionService documentLayoutExtractionService,
            ChunkingService chunkingService,
            DocumentChunkPersistenceService documentChunkPersistenceService,
            EmbeddingService embeddingService,
            ChunkEmbeddingPersistenceService chunkEmbeddingPersistenceService,
            SearchIndexUpsertService searchIndexUpsertService,
            IngestionProcessingStateService ingestionProcessingStateService,
            IngestionMetricsService ingestionMetricsService) {
        this.documentLayoutExtractionService = documentLayoutExtractionService;
        this.chunkingService = chunkingService;
        this.documentChunkPersistenceService = documentChunkPersistenceService;
        this.embeddingService = embeddingService;
        this.chunkEmbeddingPersistenceService = chunkEmbeddingPersistenceService;
        this.searchIndexUpsertService = searchIndexUpsertService;
        this.ingestionProcessingStateService = ingestionProcessingStateService;
        this.ingestionMetricsService = ingestionMetricsService;
    }

    @Override
    public void execute(IngestionWorkItem workItem) {
        ingestionProcessingStateService.markParsingStarted(workItem.documentVersionId());
        try {
            Instant parsingStartedAt = Instant.now();
            ParsedDocumentLayout parsedLayout = documentLayoutExtractionService.extract(workItem);
            ingestionProcessingStateService.recordParsingResult(workItem.documentVersionId(), parsedLayout);
            ingestionMetricsService.recordStageCompleted("parsing", Duration.between(parsingStartedAt, Instant.now()));

            Instant chunkingStartedAt = Instant.now();
            ChunkingResult chunkingResult = chunkingService.chunk(parsedLayout);
            List<DocumentChunkEntity> chunks = documentChunkPersistenceService.replaceChunks(workItem, chunkingResult);
            ingestionProcessingStateService.recordChunkingResult(workItem.documentVersionId(), chunkingResult);
            ingestionMetricsService.recordStageCompleted("chunking", Duration.between(chunkingStartedAt, Instant.now()));

            Instant embeddingStartedAt = Instant.now();
            List<ChunkEmbeddingResult> embeddings = embeddingService.embed(chunks);
            chunkEmbeddingPersistenceService.applyEmbeddings(workItem.documentVersionId(), embeddings);
            ingestionProcessingStateService.recordEmbeddingResult(workItem.documentVersionId());
            ingestionMetricsService.recordStageCompleted("embedding", Duration.between(embeddingStartedAt, Instant.now()));

            Instant indexingStartedAt = Instant.now();
            ingestionProcessingStateService.markIndexingStarted(workItem.documentVersionId());
            searchIndexUpsertService.upsertDocumentVersionChunks(workItem);
            ingestionProcessingStateService.markReady(workItem.documentVersionId());
            ingestionMetricsService.recordStageCompleted("indexing", Duration.between(indexingStartedAt, Instant.now()));
            log.info(
                    "Document parsing, chunking, embedding, and search upsert completed for document {} version {} with {} indexed chunks.",
                    workItem.documentId(),
                    workItem.versionNumber(),
                    chunks.size());
        } catch (RuntimeException exception) {
            String failingStage = determineStage(exception);
            ingestionMetricsService.recordStageFailed(failingStage);
            ingestionProcessingStateService.markFailed(workItem.documentVersionId(), exception.getMessage());
            throw exception;
        }
    }

    private String determineStage(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("document intelligence") || message.contains("blob path")) {
            return "parsing";
        }
        if (message.contains("embedding")) {
            return "embedding";
        }
        if (message.contains("search")) {
            return "indexing";
        }
        return "pipeline";
    }
}
