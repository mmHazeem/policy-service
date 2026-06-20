package com.insurance.policy.exception;

public class DocumentUploadException extends RuntimeException {
    public DocumentUploadException(String message, Throwable cause) {
        super("Failed to upload document: " + message, cause);
    }
}
