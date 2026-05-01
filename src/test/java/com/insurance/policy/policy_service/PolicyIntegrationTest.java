package com.insurance.policy.policy_service;

import com.insurance.policy.Listener.RabbitMQConfig;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.exception.ApiError;
import com.insurance.policy.repository.PolicyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PolicyIntegrationTest {

    // Define PostgreSql Container
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Container
    @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>("redis:alpine").withExposedPorts(6379);

    // Simulate real http calls
    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RabbitTemplate  rabbitTemplate;

    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired

    private RabbitAdmin rabbitAdmin;

    @Autowired
    private PolicyRepository repository;

    @BeforeEach
    void setUp() {
        // to forces the exchange/queue to be created before the test starts
        rabbitAdmin.initialize();
        repository.deleteAll();
    }

    @AfterAll
    static void stopAll() {
        rabbit.stop();
        postgres.stop();
        redis.stop();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Test
    void shouldCreateAndRetrievePolicy() {
        // Given
        PolicyRequest request = new PolicyRequest(
                "POL-999",
                "Jane Doe",
                new BigDecimal("5000.00"),
                LocalDate.now());

        // Act
        ResponseEntity<Void> response = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/v1/policies", request, Void.class);

        // Assert API accepted the message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Assert (Eventual Consistency)
        // wait for the "Side Effects" to happen in the background
        org.awaitility.Awaitility.await()
                .atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    // Check Database via API
                    ResponseEntity<PolicyResponse[]> listResponse = restTemplate
                            .withBasicAuth("admin", "admin123")
                            .getForEntity("/api/v1/policies", PolicyResponse[].class);

                    assertThat(listResponse.getBody()).isNotEmpty();
                    assertThat(listResponse.getBody()[0].policyNumber()).isEqualTo("POL-999");

                    // Check Metrics
                    double count = meterRegistry.get("insurance.policies.created").counter().count();
                    assertThat(count).isGreaterThanOrEqualTo(1.0);
                });
    }

    @Test
    void shouldMoveToDlqAfterMaxRetries() {
        // 1. Arrange: Send a 'bad' request that causes a DB constraint violation or Service error
        PolicyRequest badRequest = new PolicyRequest("FAIL-123", "", null, null);

        // 2. Act
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, badRequest);

        // 3. Assert: Wait for the retries to exhaust and message to land in DLQ
        Awaitility.await()
                .atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Try to pull one message from the DLQ
                    Object message = rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ);
                    assertThat(message).isNotNull();
                });
    }

    @Test
    void shouldReturn400WhenPolicyNumberIsMissing() {
        // Given
        PolicyRequest invalidRequest = new PolicyRequest(
                "",
                "Jane Doe",
                new BigDecimal("500.00"),
                LocalDate.now());

        // Act
        ResponseEntity<ApiError> response = restTemplate
                .withBasicAuth("admin", "admin123")
                .postForEntity("/api/v1/policies", invalidRequest, ApiError.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().getMessage()).isEqualTo("Validation Failed");
    }
}
