package com.insurance.policy.policy_service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.insurance.policy.dtos.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Validated
@Slf4j
public class PolicyService {
    private final PolicyRepository repository;
    private final PolicyMapper mapper;
    private final Counter policyCreationCounter;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PolicyService(PolicyRepository repository, PolicyMapper mapper, MeterRegistry registry,
                         OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.mapper = mapper;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;

        this.policyCreationCounter = Counter.builder("insurance.policies.created")
                .description("Total number of insurance policies created")
                .tag("type", "standard")
                .register(registry);
    }

    @Transactional
    public void createPolicyAsync(PolicyRequest request) {
        if (repository.findByPolicyNumber(request.policyNumber()).isPresent()) {
            throw new PolicyAlreadyExistsException(
                    "Policy with number '" + request.policyNumber() + "' already exists");
        }
        try {
            String payload = objectMapper.writeValueAsString(request);
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("POLICY")
                    .aggregateId(request.policyNumber())
                    .eventType("POLICY_CREATED")
                    .payload(payload)
                    .correlationId(correlationId())
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .build();
            outboxRepository.save(event);
            log.info("Outbox event saved for policy number: {}", request.policyNumber());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize policy request", e);
        }
    }

    // This method only runs if the ID is NOT in Redis
    @Cacheable(value = "policies", key = "#id")
    public PolicyResponse getPolicyById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new PolicyNotFoundException(id.toString()));
    }

    @CacheEvict(value = "policies", allEntries = true)
    public PolicyResponse updatePolicyStatus(UUID id, PolicyStatusRequest request) {
        Policy policy = repository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException(id.toString()));

        if (!policy.getStatus().canTransitionTo(request.status())) {
            throw new InvalidPolicyTransitionException(policy.getStatus(), request.status());
        }

        policy.setStatus(request.status());
        Policy saved = repository.save(policy);
        log.info("Policy {} status updated from {} to {}", id, policy.getStatus(), request.status());
        return mapper.toResponse(saved);
    }

    @CacheEvict(value = "policies", allEntries = true)
    public PolicyResponse createPolicy(PolicyRequest request) {
        log.info("Creating policy for holder: {}", request.policyHolder());

        if (repository.findByPolicyNumber(request.policyNumber()).isPresent()) {
            throw new PolicyAlreadyExistsException(
                    "Policy with number '" + request.policyNumber() + "' already exists");
        }

        Policy policy = mapper.toEntity(request);
        policy.setPremiumAmount(calculatePremium(request.coverageAmount()));
        policy.setStatus(Policy.PolicyStatus.DRAFT);

        Policy savedPolicy = repository.save(policy);

        policyCreationCounter.increment();
        return mapper.toResponse(savedPolicy);
    }

    public PageResponse<PolicyResponse> getAllPolicies(Pageable pageable) {
        Page<Policy> page = repository.findAll(pageable);
        return mapper.toResponsePage(page);
    }

    private BigDecimal calculatePremium(BigDecimal coverage) {
        return coverage.multiply(new BigDecimal("0.005"));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
