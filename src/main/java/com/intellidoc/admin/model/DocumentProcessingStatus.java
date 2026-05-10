package com.intellidoc.admin.model;

public enum DocumentProcessingStatus {
    UPLOADED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    INDEXING,
    READY,
    FAILED
}
