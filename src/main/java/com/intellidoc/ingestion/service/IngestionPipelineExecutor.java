package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.IngestionWorkItem;

public interface IngestionPipelineExecutor {

    void execute(IngestionWorkItem workItem);
}
