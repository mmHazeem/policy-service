package com.insurance.policy.dtos;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID policyId,
    String fileName,
    String contentType,
    long fileSize,
    Instant uploadedAt
) {}
