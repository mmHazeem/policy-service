package com.insurance.policy.dtos;

import com.insurance.policy.domain.Policy;
import jakarta.validation.constraints.NotNull;

public record PolicyStatusRequest(
        @NotNull Policy.PolicyStatus status
) {}
