package com.insurance.policy.Listener;

import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.exception.PolicyAlreadyExistsException;
import com.insurance.policy.policy_service.PolicyService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PolicyMessageListener {
    private final PolicyService policyService;
    private final MeterRegistry meterRegistry;

    @RabbitListener(queues = RabbitMQConfig.QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void handlePolicyCreation(PolicyRequest request) {
        log.info("Received message for policy number: {}", request.policyNumber());
        try {
            policyService.createPolicy(request);
            meterRegistry.counter("insurance.policy.created", "status", "success").increment();

        } catch (PolicyAlreadyExistsException e) {
            log.warn("Duplicate policy rejected: {}", e.getMessage());
            meterRegistry.counter("insurance.policy.created", "status", "duplicate").increment();
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);

        } catch (Exception e) {
            log.error("Failed to process policy creation for: {}. Cause: {}",
                    request.policyNumber(), e.getMessage());
            meterRegistry.counter("insurance.policy.created", "status", "failure").increment();
            throw e;
        }
    }
}