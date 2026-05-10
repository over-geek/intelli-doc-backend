package com.intellidoc.ingestion.service;

import com.azure.core.exception.HttpResponseException;
import org.springframework.stereotype.Component;

@Component
public class IngestionFailureClassifier {

    public boolean isNonRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NonRetryableIngestionException) {
                return true;
            }
            if (current instanceof HttpResponseException responseException) {
                int statusCode = responseException.getResponse() == null
                        ? 0
                        : responseException.getResponse().getStatusCode();
                String message = responseException.getMessage() == null
                        ? ""
                        : responseException.getMessage().toLowerCase();
                if (statusCode == 400
                        || statusCode == 401
                        || statusCode == 403
                        || message.contains("public access is disabled")
                        || message.contains("private endpoint")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
