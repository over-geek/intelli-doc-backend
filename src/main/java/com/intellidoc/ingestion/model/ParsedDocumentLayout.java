package com.intellidoc.ingestion.model;

import java.util.List;

public record ParsedDocumentLayout(
        int totalPages,
        int paragraphCount,
        int tableCount,
        List<DocumentLayoutBlock> blocks) {
}
