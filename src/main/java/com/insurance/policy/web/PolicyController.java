package com.insurance.policy.web;

import com.insurance.policy.dtos.PageResponse;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.dtos.PolicyStatusRequest;
import com.insurance.policy.policy_service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policy Management", description = "Operations for insurance policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> createPolicyAsync(@Valid @RequestBody PolicyRequest request) {
        policyService.createPolicyAsync(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get all policies with pagination")
    public PageResponse<PolicyResponse> list(@ParameterObject @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return policyService.getAllPolicies(pageable);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update policy status with enforced transitions: DRAFT → ACTIVE → CANCELLED")
    public ResponseEntity<PolicyResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody PolicyStatusRequest request) {
        PolicyResponse response = policyService.updatePolicyStatus(id, request);
        return ResponseEntity.ok(response);
    }
}
