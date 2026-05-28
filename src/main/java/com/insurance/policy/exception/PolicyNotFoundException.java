package com.insurance.policy.exception;

public class PolicyNotFoundException extends RuntimeException {
    public PolicyNotFoundException(String id) {
        super("Policy not found with id: " + id);
    }
}