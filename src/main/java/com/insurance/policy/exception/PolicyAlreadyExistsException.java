package com.insurance.policy.exception;

public class PolicyAlreadyExistsException extends RuntimeException {
    public PolicyAlreadyExistsException(String message) {
        super(message);
    }
}
