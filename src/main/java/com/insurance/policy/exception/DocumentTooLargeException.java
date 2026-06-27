package com.insurance.policy.exception;

public class DocumentTooLargeException extends RuntimeException {
    public DocumentTooLargeException(Long fileSize) {
        super("File is too large, Failed to upload document: " + fileSize);
    }
}
