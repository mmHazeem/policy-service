package com.insurance.policy.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyResponse(
        UUID id,
        String policyNumber,
        String policyHolder,
        BigDecimal coverageAmount,
        BigDecimal premiumAmount,
        LocalDate startDate,
        String status
) {}