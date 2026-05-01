package com.insurance.policy.policy_service;

import com.insurance.policy.domain.Policy;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.repository.PolicyRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PolicyPerformanceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:alpine").withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PolicyRepository repository;

    @Test
    void shouldHandleAsyncCreationAndCaching() {
        PolicyRequest request = new PolicyRequest("ASYNC-1", "Fast User", new BigDecimal("1000"), LocalDate.now());

        // 1. ACT: Call the API
        ResponseEntity<Void> response = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/v1/policies", request, Void.class);

        // 2. ASSERT: API responds instantly (202 Accepted)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 3. WAIT: Use Awaitility to wait for RabbitMQ to process and Save to DB
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    Optional<Policy> saved = repository.findByPolicyNumber("ASYNC-1");
                    assertThat(saved).isPresent();
                });
    }

}
