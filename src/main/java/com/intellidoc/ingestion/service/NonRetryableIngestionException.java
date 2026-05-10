package com.intellidoc.ingestion.service;

public class NonRetryableIngestionException extends RuntimeException {

    public NonRetryableIngestionException(String message) {
        super(message);
    }
}
