package com.insurance.policy.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyStatusTest {

    @Test
    void shouldAllowDraftToActive() {
        assertTrue(Policy.PolicyStatus.DRAFT.canTransitionTo(Policy.PolicyStatus.ACTIVE));
    }

    @Test
    void shouldAllowActiveToCancelled() {
        assertTrue(Policy.PolicyStatus.ACTIVE.canTransitionTo(Policy.PolicyStatus.CANCELLED));
    }

    @Test
    void shouldRejectDraftToCancelled() {
        assertFalse(Policy.PolicyStatus.DRAFT.canTransitionTo(Policy.PolicyStatus.CANCELLED));
    }

    @Test
    void shouldRejectActiveToDraft() {
        assertFalse(Policy.PolicyStatus.ACTIVE.canTransitionTo(Policy.PolicyStatus.DRAFT));
    }

    @Test
    void shouldRejectCancelledToDraft() {
        assertFalse(Policy.PolicyStatus.CANCELLED.canTransitionTo(Policy.PolicyStatus.DRAFT));
    }

    @Test
    void shouldRejectCancelledToActive() {
        assertFalse(Policy.PolicyStatus.CANCELLED.canTransitionTo(Policy.PolicyStatus.ACTIVE));
    }

    @Test
    void shouldRejectSameStateTransition() {
        assertFalse(Policy.PolicyStatus.DRAFT.canTransitionTo(Policy.PolicyStatus.DRAFT));
        assertFalse(Policy.PolicyStatus.ACTIVE.canTransitionTo(Policy.PolicyStatus.ACTIVE));
        assertFalse(Policy.PolicyStatus.CANCELLED.canTransitionTo(Policy.PolicyStatus.CANCELLED));
    }
}
