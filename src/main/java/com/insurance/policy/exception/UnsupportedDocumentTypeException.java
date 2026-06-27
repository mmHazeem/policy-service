package com.insurance.policy.exception;

public class UnsupportedDocumentTypeException extends RuntimeException {
    public UnsupportedDocumentTypeException(String fileContentType) {
        super( "This File form is not allowed to upload" + fileContentType);
    }
}
