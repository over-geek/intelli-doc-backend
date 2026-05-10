package com.intellidoc.ingestion.model;

public record ChunkCandidate(
        int chunkIndex,
        String content,
        Integer pageNumber,
        String sectionHeading,
        Integer startCharOffset,
        Integer endCharOffset) {
}
