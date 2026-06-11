# Circuit Breaker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wrap `RabbitTemplate.convertAndSend()` in the outbox publisher with a Resilience4j circuit breaker.

**Architecture:** A `@CircuitBreaker` annotation on `publishPendingEvents()` with a no-op fallback that logs and skips publishing. The controller is untouched — no new imports, no circuit awareness, no API changes.

**Tech Stack:** resilience4j-spring-boot3, Spring Boot 3.4, RabbitMQ, Outbox Pattern

---

### Task 1: Add Resilience4j dependency

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add resilience4j-spring-boot3 to pom.xml**

After the `opentelemetry-exporter-zipkin` block (around line 115), add:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
```

- [ ] **Step 2: Commit**

```bash
git add pom.xml
git commit -m "build: add resilience4j-spring-boot3 dependency"
```

---

### Task 2: Configure circuit breaker

**Files:**
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Add resilience4j config to application.yaml**

After the `app.outbox.poll-interval` block and before `app.security`, add:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
    instances:
      rabbitmq:
        base-config: default
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.yaml
git commit -m "config: add resilience4j circuit breaker for rabbitmq"
```

---

### Task 3: Add @CircuitBreaker to OutboxPublisher

**Files:**
- Modify: `src/main/java/com/insurance/policy/outbox/OutboxPublisher.java`

- [ ] **Step 1: Add import and annotation to OutboxPublisher**

Current imports need `io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker` added.

Add `@CircuitBreaker` to `publishPendingEvents()` and add the fallback method:

```java
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
// ... existing imports

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:5000}")
    @Transactional
    @CircuitBreaker(name = "rabbitmq", fallbackMethod = "handleCircuitOpen")
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(
                OutboxEvent.OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                PolicyRequest payload = objectMapper.readValue(event.getPayload(), PolicyRequest.class);
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, payload);
                event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                outboxRepository.save(event);
                log.info("Published outbox event {} of type {}", event.getId(), event.getEventType());
            } catch (JsonProcessingException e) {
                log.error("Corrupt payload for outbox event {}: {}", event.getId(), e.getMessage());
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                outboxRepository.save(event);
            } catch (AmqpException e) {
                log.error("Failed to publish outbox event {} to RabbitMQ: {}", event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    log.warn("Outbox event {} failed after {} retries", event.getId(), event.getMaxRetries());
                }
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Unexpected error publishing outbox event {}: {}", event.getId(), e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= event.getMaxRetries()) {
                    event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                }
                outboxRepository.save(event);
            }
        }
    }

    @SuppressWarnings("unused")
    private void handleCircuitOpen(Exception e) {
        log.warn("RabbitMQ circuit breaker is open. Skipping outbox publishing. Cause: {}", e.getMessage());
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/insurance/policy/outbox/OutboxPublisher.java
git commit -m "feat: add circuit breaker to outbox publisher"
```

---

### Task 4: Update OutboxPublisherTest

**Files:**
- Modify: `src/test/java/com/insurance/policy/outbox/OutboxPublisherTest.java`

- [ ] **Step 1: Add test for circuit breaker fallback**

The existing `shouldRetryWhenPublishingFails` test throws a `RuntimeException` which hits the generic `catch (Exception e)` block. With the circuit breaker, the `RuntimeException` would be caught by the circuit breaker too, but the fallback method runs on circuit open — the existing retry test still validates the retry logic inside `publishPendingEvents()`.

Add a new test that verifies the circuit breaker annotation is present on the method:

```java
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
// ... existing imports

// Add after setUp()
@Test
void shouldHaveCircuitBreakerAnnotationOnPublishMethod() throws Exception {
    var method = OutboxPublisher.class.getMethod("publishPendingEvents");
    var annotation = method.getAnnotation(CircuitBreaker.class);
    assertEquals("rabbitmq", annotation.name());
    assertEquals("handleCircuitOpen", annotation.fallbackMethod());
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn test -pl . -Dtest=OutboxPublisherTest -DskipTests=false`
Expected: All 4 tests pass (3 existing + 1 new)

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/insurance/policy/outbox/OutboxPublisherTest.java
git commit -m "test: verify circuit breaker annotation on outbox publisher"
```

---

### Task 5: Run full test suite

- [ ] **Step 1: Run all tests**

```bash
mvn test
```

Expected: All tests pass (21 existing + any new)

- [ ] **Step 2: Commit remaining changes**

```bash
git add -A
git commit -m "feat: add Resilience4j circuit breaker for RabbitMQ outbox publishing"
```
