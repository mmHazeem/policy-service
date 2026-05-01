package com.insurance.policy.policy_service;

import com.insurance.policy.domain.Policy;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock private PolicyRepository repository;
    @Mock
    private PolicyMapper mapper;
    @Spy
    private MeterRegistry registry = new SimpleMeterRegistry(); // Real registry to track metrics

    private PolicyService policyService;

    @Captor
    private ArgumentCaptor<Policy> policyCaptor;

    @BeforeEach
    void setUp() {
        policyService = new PolicyService(repository, mapper, registry);
    }

    @Test
    void shouldSavePolicyWithCorrectCalculatedPremium() {
        // Given
        PolicyRequest request = new PolicyRequest("J-100", "Jan Max", new BigDecimal("1000"), LocalDate.now());
        when(mapper.toEntity(any())).thenReturn(new Policy());

        // Act
        policyService.createPolicy(request);

        // Assert
        verify(repository).save(policyCaptor.capture());
        Policy savedPolicy = policyCaptor.getValue();

        // Verify
        assertEquals(new BigDecimal("5.000"), savedPolicy.getPremiumAmount());
    }

    @Test
    void shouldThrowExceptionWhenDatabaseFails() {
        // Given
        PolicyRequest request = new PolicyRequest("J-100", "Jan Max", new BigDecimal("1000"), LocalDate.now());
        when(mapper.toEntity(any())).thenReturn(new Policy());

        // Tell the mock to explode!
        when(repository.save(any())).thenThrow(new RuntimeException("DB Connection Lost"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            policyService.createPolicy(request);
        });
    }

    @Test
    void shouldNotSaveWhenValidationFails() {
        // Act
        try {
            policyService.createPolicy(null);
        } catch (Exception e) {
            // Expected
        }

        // Assert: Prove the database was never even touched
        verifyNoInteractions(repository);
    }
    @Test
    void shouldIncrementCounterWhenPolicyCreated() {
        // Given
        PolicyRequest request = new PolicyRequest("P-100", "John Doe", new BigDecimal("1000"), LocalDate.now());
        Policy dummyPolicy = new Policy();
        // When
        when(mapper.toEntity(any(PolicyRequest.class))).thenReturn(dummyPolicy);
        when(repository.save(any(Policy.class))).thenReturn(dummyPolicy);
        policyService.createPolicy(request);

        // assert
        double count = registry.get("insurance.policies.created").counter().count();
        assertEquals(1.0, count);
    }

    @Test
    void createPolicy() {
    }

    @Test
    void getAllPolicies() {
        // Given
        Policy dummyPolicy = new Policy();
        dummyPolicy.setPolicyHolder("John Doe");

        PolicyResponse dummyResponse = new PolicyResponse(
                UUID.randomUUID(), "P-100", "John Doe",
                new BigDecimal("1000"), new BigDecimal("5"),
                LocalDate.now(), "DRAFT"
        );

        // When
        when(repository.findAll()).thenReturn(List.of(dummyPolicy));
        when(mapper.toResponseList(Collections.singletonList(dummyPolicy))).thenReturn(List.of(dummyResponse));

        // Act
        List<PolicyResponse> result = policyService.getAllPolicies();

        // Then
        assertEquals(1, result.size());
        assertEquals("John Doe", result.get(0).policyHolder());

        verify(repository, times(1)).findAll();
        verify(mapper, times(1)).toResponseList(anyList());

    }
}
