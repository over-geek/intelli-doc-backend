package com.intellidoc.ingestion.service;

import com.intellidoc.ingestion.model.ChunkingResult;
import com.intellidoc.ingestion.model.ParsedDocumentLayout;

public interface ChunkingService {

    ChunkingResult chunk(ParsedDocumentLayout parsedLayout);
}
