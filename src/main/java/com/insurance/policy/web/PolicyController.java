package com.insurance.policy.web;

import com.insurance.policy.Listener.RabbitMQConfig;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.policy_service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policy Management", description = "Operations for insurance policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final RabbitTemplate rabbitTemplate;

    @PostMapping
    public ResponseEntity<Void> createPolicyAsync(@Valid @RequestBody PolicyRequest request) {
        // Send the request to RabbitMQ instead of calling the service directly
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, request);

        // Return 202 Accepted immediately
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    @Operation(summary = "Get all policies")
    public List<PolicyResponse> list() {
        return policyService.getAllPolicies();
    }
}
