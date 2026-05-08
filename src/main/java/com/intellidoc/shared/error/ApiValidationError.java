package com.intellidoc.shared.error;

public record ApiValidationError(
        String field,
        Object rejectedValue,
        String message) {
}
