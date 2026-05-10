package com.intellidoc.search.service;

import com.azure.search.documents.SearchClient;
import com.azure.search.documents.models.IndexDocumentsResult;
import com.azure.search.documents.models.IndexingResult;
import com.intellidoc.admin.model.DocumentAccessPolicyEntity;
import com.intellidoc.admin.repository.DocumentAccessPolicyRepository;
import com.intellidoc.ingestion.model.DocumentChunkEntity;
import com.intellidoc.ingestion.model.IngestionWorkItem;
import com.intellidoc.ingestion.repository.DocumentChunkRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchIndexUpsertService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexUpsertService.class);

    private final SearchClient searchClient;
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentAccessPolicyRepository documentAccessPolicyRepository;
    private final SearchChunkDocumentMapper searchChunkDocumentMapper;

    public SearchIndexUpsertService(
            SearchClient searchClient,
            DocumentChunkRepository documentChunkRepository,
            DocumentAccessPolicyRepository documentAccessPolicyRepository,
            SearchChunkDocumentMapper searchChunkDocumentMapper) {
        this.searchClient = searchClient;
        this.documentChunkRepository = documentChunkRepository;
        this.documentAccessPolicyRepository = documentAccessPolicyRepository;
        this.searchChunkDocumentMapper = searchChunkDocumentMapper;
    }

    @Transactional
    public void upsertDocumentVersionChunks(IngestionWorkItem workItem) {
        List<DocumentChunkEntity> chunks =
                documentChunkRepository.findByDocumentVersionIdOrderByChunkIndexAsc(workItem.documentVersionId());
        List<DocumentAccessPolicyEntity> accessPolicies =
                documentAccessPolicyRepository.findByDocumentIdOrderByAccessTypeAscAccessValueAsc(workItem.documentId());

        List<Map<String, Object>> documents = chunks.stream()
                .map(chunk -> searchChunkDocumentMapper.map(chunk, workItem.documentVersion(), accessPolicies))
                .toList();

        if (documents.isEmpty()) {
            log.info("Skipping Azure AI Search upsert for document {} because no chunks were found.", workItem.documentId());
            return;
        }

        IndexDocumentsResult result = searchClient.mergeOrUploadDocuments(documents);
        Map<String, IndexingResult> resultsByKey = result.getResults().stream()
                .collect(Collectors.toMap(IndexingResult::getKey, Function.identity()));

        for (DocumentChunkEntity chunk : chunks) {
            String key = chunk.getId().toString();
            IndexingResult indexingResult = resultsByKey.get(key);
            if (indexingResult == null || !Boolean.TRUE.equals(indexingResult.isSucceeded())) {
                throw new IllegalStateException("Azure AI Search upsert failed for chunk " + key + ".");
            }
            chunk.setAiSearchDocId(key);
        }

        documentChunkRepository.saveAll(chunks);
        log.info(
                "Upserted {} chunk documents into Azure AI Search for document {} version {}.",
                chunks.size(),
                workItem.documentId(),
                workItem.versionNumber());
    }
}
