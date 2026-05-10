package com.intellidoc.ingestion.model;

import java.util.List;

public record ChunkingResult(List<ChunkCandidate> chunks) {
}
