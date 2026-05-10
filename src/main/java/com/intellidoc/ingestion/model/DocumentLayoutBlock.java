package com.intellidoc.ingestion.model;

public record DocumentLayoutBlock(
        int blockIndex,
        DocumentLayoutBlockType blockType,
        String content,
        Integer pageNumber,
        String paragraphRole,
        Integer startOffset,
        Integer endOffset,
        Integer tableRowCount,
        Integer tableColumnCount) {
}
