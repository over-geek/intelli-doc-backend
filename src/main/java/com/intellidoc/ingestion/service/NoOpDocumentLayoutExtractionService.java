package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.IngestionWorkItem;
import com.intellidoc.ingestion.model.ParsedDocumentLayout;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(DocumentLayoutExtractionService.class)
public class NoOpDocumentLayoutExtractionService implements DocumentLayoutExtractionService {

    private static final Logger log = LoggerFactory.getLogger(NoOpDocumentLayoutExtractionService.class);

    @Override
    public ParsedDocumentLayout extract(IngestionWorkItem workItem) {
        log.info(
                "Skipping Document Intelligence extraction for document {} version {} because the integration is disabled.",
                workItem.documentId(),
                workItem.versionNumber());
        return new ParsedDocumentLayout(0, 0, 0, List.of());
    }
}
