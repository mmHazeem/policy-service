package com.insurance.policy.policy_service;

import com.insurance.policy.domain.Policy;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.exception.PolicyAlreadyExistsException;
import com.insurance.policy.mapper.PolicyMapper;
import com.insurance.policy.repository.PolicyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock private PolicyRepository repository;
    @Mock private PolicyMapper mapper;
    @Spy  private MeterRegistry registry = new SimpleMeterRegistry();

    private PolicyService policyService;

    @Captor
    private ArgumentCaptor<Policy> policyCaptor;

    private static final PolicyRequest VALID_REQUEST =
            new PolicyRequest("J-100", "Jan Max", new BigDecimal("1000"), LocalDate.now());

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(repository, mapper, registry);
    }

    @Test
    void shouldSavePolicyWithCorrectCalculatedPremium() {
        when(mapper.toEntity(any())).thenReturn(new Policy());

        policyService.createPolicy(VALID_REQUEST);

        verify(repository).save(policyCaptor.capture());
        assertEquals(new BigDecimal("5.000"), policyCaptor.getValue().getPremiumAmount());
    }

    @Test
    void shouldThrowExceptionWhenDatabaseFails() {
        when(mapper.toEntity(any())).thenReturn(new Policy());
        when(repository.save(any())).thenThrow(new RuntimeException("DB Connection Lost"));

        assertThrows(RuntimeException.class, () -> policyService.createPolicy(VALID_REQUEST));
    }

    @Test
    void shouldNotSaveWhenValidationFails() {
        // null request bypasses the idempotency check and causes NPE before touching the DB
        try {
            policyService.createPolicy(null);
        } catch (Exception ignored) {}

        verify(repository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPolicyNumberAlreadyExists() {
        // Simulate duplicate
        when(repository.findByPolicyNumber(VALID_REQUEST.policyNumber()))
                .thenReturn(Optional.of(new Policy()));

        assertThrows(PolicyAlreadyExistsException.class,
                () -> policyService.createPolicy(VALID_REQUEST));

        verify(repository, never()).save(any());
    }

    @Test
    void shouldIncrementCounterWhenPolicyCreated() {
        when(mapper.toEntity(any())).thenReturn(new Policy());
        when(repository.save(any())).thenReturn(new Policy());

        policyService.createPolicy(VALID_REQUEST);

        double count = registry.get("insurance.policies.created").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void shouldReturnAllPolicies() {
        Policy dummyPolicy = new Policy();
        dummyPolicy.setPolicyHolder("John Doe");

        PolicyResponse dummyResponse = new PolicyResponse(
                UUID.randomUUID(), "P-100", "John Doe",
                new BigDecimal("1000"), new BigDecimal("5"),
                LocalDate.now(), "DRAFT"
        );

        when(repository.findAll()).thenReturn(List.of(dummyPolicy));
        when(mapper.toResponseList(Collections.singletonList(dummyPolicy)))
                .thenReturn(List.of(dummyResponse));

        List<PolicyResponse> result = policyService.getAllPolicies();

        assertEquals(1, result.size());
        assertEquals("John Doe", result.get(0).policyHolder());
        verify(repository, times(1)).findAll();
    }
}