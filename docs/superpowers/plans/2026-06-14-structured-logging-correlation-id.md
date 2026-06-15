# Structured Logging + Correlation ID Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add structured JSON logging with correlation ID traceable across HTTP → outbox → RabbitMQ → consumer.

**Architecture:** A `CorrelationIdFilter` manages MDC per HTTP request; the correlation ID is persisted in `OutboxEvent.correlationId` for the scheduled publisher thread, propagated via RabbitMQ message headers, and extracted by the consumer.

**Tech Stack:** logstash-logback-encoder, Spring Security OncePerRequestFilter, SLF4J MDC, Spring AMQP MessagePostProcessor/@Header

---

### Task 1: Add logstash-logback-encoder + logback-spring.xml

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/logback-spring.xml`
- Verify: `application.yaml` (no changes needed)

- [ ] **Step 1: Add dependency to pom.xml**

Add inside `<dependencies>`:
```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.1</version>
</dependency>
```

- [ ] **Step 2: Create logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProperty name="service" source="spring.application.name" default="policy-service"/>

    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp>
                    <timeZone>UTC</timeZone>
                </timestamp>
                <mdc>
                    <include>correlationId</include>
                </mdc>
                <logLevel/>
                <loggerName/>
                <threadName/>
                <message/>
                <arguments/>
                <stackTrace/>
            </providers>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
```

- [ ] **Step 3: Verify JSON logs**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

---

### Task 2: Create CorrelationIdFilter

**Files:**
- Create: `src/main/java/com/insurance/policy/config/CorrelationIdFilter.java`
- Modify: `src/main/java/com/insurance/policy/config/SecurityConfig.java`

- [ ] **Step 1: Write the failing test**

File: `src/test/java/com/insurance/policy/web/CorrelationIdFilterTest.java`
```java
package com.insurance.policy.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DummyController.class)
class CorrelationIdFilterTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() throws Exception {
        mockMvc.perform(get("/_test"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void shouldPropagateCorrelationIdFromRequestHeader() throws Exception {
        mockMvc.perform(get("/_test").header("X-Correlation-Id", "test-id-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-id-123"));
    }
}
```

- [ ] **Step 2: Create CorrelationIdFilter**

```java
package com.insurance.policy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        try {
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

- [ ] **Step 3: Register in SecurityConfig**

Add after `addFilterBefore(jwtAuthFilter, ...)` line:
```java
.addFilterBefore(correlationIdFilter, JwtAuthenticationFilter.class)
```

Declare the field:
```java
private final CorrelationIdFilter correlationIdFilter;
```

- [ ] **Step 4: Create DummyController for tests**

```java
package com.insurance.policy.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DummyController {
    @GetMapping("/_test")
    public String test() {
        return "ok";
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn test -pl . -Dtest=CorrelationIdFilterTest`
Expected: 2 PASS, 0 FAIL

---

### Task 3: Add correlationId to OutboxEvent

**Files:**
- Modify: `src/main/java/com/insurance/policy/domain/OutboxEvent.java`
- Create: `src/main/resources/db/migration/V4__add_correlation_id.sql`
- Modify: `src/main/java/com/insurance/policy/policy_service/PolicyService.java`

- [ ] **Step 1: Add field to entity**

```java
@Column(name = "correlation_id", length = 36)
private String correlationId;
```

- [ ] **Step 2: Create Flyway migration**

File: `src/main/resources/db/migration/V4__add_correlation_id.sql`
```sql
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(36);
```

- [ ] **Step 3: Set correlationId in PolicyService.createPolicyAsync()**

Find `OutboxEvent event = OutboxEvent.builder()` block in `PolicyService.java` and add:
```java
.correlationId(correlationId())
```

Add the helper method to `PolicyService`:
```java
private String correlationId() {
    String id = MDC.get("correlationId");
    return id != null ? id : UUID.randomUUID().toString();
}
```

Add imports:
```java
import org.slf4j.MDC;
import java.util.UUID;
```

---

### Task 4: Propagate correlationId through RabbitMQ

**Files:**
- Modify: `src/main/java/com/insurance/policy/outbox/OutboxPublisher.java`
- Modify: `src/main/java/com/insurance/policy/Listener/PolicyMessageListener.java`

- [ ] **Step 1: Set MDC + header in OutboxPublisher**

In the try block of `publishPendingEvents()`, before `rabbitTemplate.convertAndSend()`:
```java
MDC.put("correlationId", event.getCorrelationId());
```

Add MessagePostProcessor to `rabbitTemplate.convertAndSend()`:
```java
rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, payload,
        message -> {
            message.getMessageProperties().setHeader("correlationId", event.getCorrelationId());
            return message;
        });
```

Add import:
```java
import org.slf4j.MDC;
```

- [ ] **Step 2: Extract correlationId in PolicyMessageListener**

Change method signature to accept `@Header`:
```java
public void handlePolicyCreation(PolicyRequest request,
                                  @Header("correlationId") String correlationId) {
```

Wrap body in try/finally with MDC:
```java
MDC.put("correlationId", correlationId);
try {
    // ... existing body
} finally {
    MDC.clear();
}
```

Add imports:
```java
import org.slf4j.MDC;
import org.springframework.messaging.handler.annotation.Header;
```

---

### Task 5: Write and run tests

**Files:**
- Create: `src/test/java/com/insurance/policy/web/CorrelationIdFilterTest.java` (already done in Task 2)
- Modify: `src/test/java/com/insurance/policy/policy_service/PolicyIntegrationTest.java`

- [ ] **Step 1: Add integration test for correlation flow**

In `PolicyIntegrationTest.java`, add:

```java
@Test
void shouldReturnCorrelationIdHeaderOnCreate() {
    PolicyRequest request = new PolicyRequest(
            "POL-CID-001", "Correlation Test",
            new BigDecimal("5000.00"), LocalDate.now());

    ResponseEntity<Void> response = restTemplate.exchange(
            "/api/v1/policies", HttpMethod.POST,
            new HttpEntity<>(request, bearerHeaders(jwtToken)),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().containsKey("X-Correlation-Id")).isTrue();
}
```

- [ ] **Step 2: Run all tests**

Run: `mvn test`
Expected: All tests pass, output is JSON format in console

---

### Task 6: Clean up

- [ ] **Remove DummyController** if tests are refactored to use an existing controller
