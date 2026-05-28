package com.insurance.policy.policy_service;

import com.insurance.policy.domain.Policy;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.exception.PolicyAlreadyExistsException;
import com.insurance.policy.exception.PolicyNotFoundException;
import com.insurance.policy.mapper.PolicyMapper;
import com.insurance.policy.repository.PolicyRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

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

    public PolicyService(PolicyRepository repository, PolicyMapper mapper, MeterRegistry registry) {
        this.repository = repository;
        this.mapper = mapper;

        this.policyCreationCounter = Counter.builder("insurance.policies.created")
                .description("Total number of insurance policies created")
                .tag("type", "standard")
                .register(registry);
    }

    // This method only runs if the ID is NOT in Redis
    @Cacheable(value = "policies", key = "#id")
    public PolicyResponse getPolicyById(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .orElseThrow(() -> new PolicyNotFoundException(id.toString()));
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

        // Increment the metric every time a policy is successfully saved
        System.out.println("Incrementing counter...");
        policyCreationCounter.increment();
        System.out.println("Metric incremented!");
        return mapper.toResponse(savedPolicy);
    }

    public List<PolicyResponse> getAllPolicies() {
        return mapper.toResponseList(repository.findAll());
    }

    private BigDecimal calculatePremium(BigDecimal coverage) {
        return coverage.multiply(new BigDecimal("0.005"));
    }
}
