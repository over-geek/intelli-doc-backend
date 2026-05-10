package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.IngestionWorkItem;
import com.intellidoc.ingestion.model.ParsedDocumentLayout;

public interface DocumentLayoutExtractionService {

    ParsedDocumentLayout extract(IngestionWorkItem workItem);
}
