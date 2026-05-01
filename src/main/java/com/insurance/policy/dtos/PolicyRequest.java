package com.insurance.policy.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PolicyRequest(
        @NotBlank String policyNumber,
        @NotBlank String policyHolder,
        @NotNull @DecimalMin("1000.00") BigDecimal coverageAmount,
        @NotNull LocalDate startDate
) {}
