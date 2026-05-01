package com.insurance.policy.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_number", unique = true, nullable = false)
    private String policyNumber;

    @Column(name = "policy_holder")
    private String policyHolder;

    @Column(name = "coverage_amount", precision = 19, scale = 2)
    private BigDecimal coverageAmount;

    @Column(name = "premium_amount", precision = 19, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    private PolicyStatus status;

    public enum PolicyStatus {
        DRAFT, ACTIVE, CANCELLED
    }
}
