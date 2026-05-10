package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.IngestionWorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(IngestionPipelineExecutor.class)
public class NoOpIngestionPipelineExecutor implements IngestionPipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(NoOpIngestionPipelineExecutor.class);

    @Override
    public void execute(IngestionWorkItem workItem) {
        log.info(
                "Ingestion worker accepted document {} version {}. Downstream parsing/chunking/indexing stages will be added in subsequent Phase 3 features.",
                workItem.documentId(),
                workItem.versionNumber());
    }
}
