package com.insurance.policy.web;

import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.policy_service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policy Management", description = "Operations for insurance policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    public ResponseEntity<Void> createPolicyAsync(@Valid @RequestBody PolicyRequest request) {
        policyService.createPolicyAsync(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    @Operation(summary = "Get all policies")
    public List<PolicyResponse> list() {
        return policyService.getAllPolicies();
    }
}
