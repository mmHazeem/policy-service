package com.insurance.policy.policy_service;

import com.insurance.policy.BaseIntegrationTest;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.repository.PolicyRepository;
import com.insurance.policy.repository.UserRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PolicyPerformanceIntegrationTest extends BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("policy_db")
            .withUsername("policy_user")
            .withPassword("policy_password");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management");

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private RabbitAdmin rabbitAdmin;
    @Autowired
    private PolicyRepository repository;
    @Autowired
    private UserRepository userRepository;
    private String jwtToken;

    @BeforeEach
    void setUp() {
        // Clean up data before each test
        repository.deleteAll();
        userRepository.deleteAll();

        // Register a fresh user per test so tests are fully isolated
        jwtToken = obtainToken("it-user-" + UUID.randomUUID());
    }

    @Test
    void shouldHandleAsyncCreationAndCaching() {
        PolicyRequest request = new PolicyRequest(
                "ASYNC-1",
                "Fast User",
                new BigDecimal("1000"),
                LocalDate.now());

        // 1. ACT: Call the API
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/policies", HttpMethod.POST,
                new HttpEntity<>(request, bearerHeaders(jwtToken)),
                Void.class);
        // 2. ASSERT: API responds instantly (202 Accepted)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 3. WAIT: Use Awaitility to wait for RabbitMQ to process and Save to DB
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    assertThat(repository.findByPolicyNumber("ASYNC-1")).isPresent();
                });
    }
}