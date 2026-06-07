package com.insurance.policy.exception;

import com.insurance.policy.domain.Policy;

public class InvalidPolicyTransitionException extends RuntimeException {
    public InvalidPolicyTransitionException(Policy.PolicyStatus from, Policy.PolicyStatus to) {
        super("Cannot transition policy from " + from + " to " + to);
    }
}
