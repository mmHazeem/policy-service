package com.insurance.policy.policy_service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insurance.policy.domain.OutboxEvent;
import com.insurance.policy.domain.Policy;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.dtos.PolicyStatusRequest;
import com.insurance.policy.exception.InvalidPolicyTransitionException;
import com.insurance.policy.exception.PolicyAlreadyExistsException;
import com.insurance.policy.exception.PolicyNotFoundException;
import com.insurance.policy.mapper.PolicyMapper;
import com.insurance.policy.repository.OutboxRepository;
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
    @Mock private OutboxRepository outboxRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Spy  private MeterRegistry registry = new SimpleMeterRegistry();

    private PolicyService policyService;

    @Captor
    private ArgumentCaptor<Policy> policyCaptor;
    @Captor
    private ArgumentCaptor<OutboxEvent> outboxCaptor;

    private static final PolicyRequest VALID_REQUEST =
            new PolicyRequest("J-100", "Jan Max", new BigDecimal("1000"), LocalDate.now());

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        policyService = new PolicyService(repository, mapper, registry, outboxRepository, objectMapper);
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
    void shouldSaveOutboxEventWhenCreatePolicyAsyncIsCalled() {
        when(repository.findByPolicyNumber(VALID_REQUEST.policyNumber())).thenReturn(Optional.empty());

        policyService.createPolicyAsync(VALID_REQUEST);

        verify(outboxRepository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();
        assertEquals("POLICY", saved.getAggregateType());
        assertEquals(VALID_REQUEST.policyNumber(), saved.getAggregateId());
        assertEquals("POLICY_CREATED", saved.getEventType());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, saved.getStatus());
    }

    @Test
    void shouldThrowWhenCreatePolicyAsyncWithDuplicatePolicyNumber() {
        when(repository.findByPolicyNumber(VALID_REQUEST.policyNumber()))
                .thenReturn(Optional.of(new Policy()));

        assertThrows(PolicyAlreadyExistsException.class,
                () -> policyService.createPolicyAsync(VALID_REQUEST));

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void shouldUpdatePolicyStatusSuccessfully() {
        Policy existingPolicy = new Policy();
        existingPolicy.setId(UUID.randomUUID());
        existingPolicy.setPolicyNumber("POL-001");
        existingPolicy.setStatus(Policy.PolicyStatus.DRAFT);

        when(repository.findById(existingPolicy.getId())).thenReturn(Optional.of(existingPolicy));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any())).thenReturn(new PolicyResponse(
                existingPolicy.getId(), "POL-001", "John", null, null, null, "ACTIVE"));

        PolicyResponse result = policyService.updatePolicyStatus(
                existingPolicy.getId(), new PolicyStatusRequest(Policy.PolicyStatus.ACTIVE));

        assertEquals("ACTIVE", result.status());
        assertEquals(Policy.PolicyStatus.ACTIVE, existingPolicy.getStatus());
    }

    @Test
    void shouldThrowWhenTransitionIsInvalid() {
        Policy existingPolicy = new Policy();
        existingPolicy.setId(UUID.randomUUID());
        existingPolicy.setStatus(Policy.PolicyStatus.DRAFT);

        when(repository.findById(existingPolicy.getId())).thenReturn(Optional.of(existingPolicy));

        assertThrows(InvalidPolicyTransitionException.class,
                () -> policyService.updatePolicyStatus(
                        existingPolicy.getId(), new PolicyStatusRequest(Policy.PolicyStatus.CANCELLED)));
    }

    @Test
    void shouldThrowWhenPolicyNotFoundForStatusUpdate() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(PolicyNotFoundException.class,
                () -> policyService.updatePolicyStatus(
                        id, new PolicyStatusRequest(Policy.PolicyStatus.ACTIVE)));
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