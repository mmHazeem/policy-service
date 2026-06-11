# Circuit Breaker for RabbitMQ Outbox Publishing

## Problem

The `OutboxPublisher` scheduler polls pending events every 5 seconds and calls `RabbitTemplate.convertAndSend()`. When RabbitMQ is down, every poll cycle wastes a connection attempt, spams logs with `AmqpException`, and burns scheduler threads on a known-failing operation.

## Constraint

The `POST /api/v1/policies` controller must remain completely unaware of RabbitMQ. No new imports, no circuit state reads, no response headers. The outbox pattern exists to decouple the controller from infrastructure — the circuit breaker must respect that boundary.

## Solution

Add a Resilience4j circuit breaker around `RabbitTemplate.convertAndSend()` inside `OutboxPublisher.publishPendingEvents()`.

### Component changes

**pom.xml** — add `resilience4j-spring-boot3` dependency.

**application.yaml** — configure the circuit breaker:
- sliding window of 5 calls
- 50% failure rate threshold
- 30 second wait in open state
- 3 permitted calls in half-open state

**OutboxPublisher** — annotate `publishPendingEvents()`:
- `@CircuitBreaker(name = "rabbitmq", fallbackMethod = "handleCircuitOpen")`
- Fallback: log warning, skip publishing, events stay PENDING in outbox

### Behavior

| State | What happens |
|-------|-------------|
| **Closed** (RabbitMQ healthy) | Normal flow: events are published, marked PUBLISHED |
| **Open** (RabbitMQ down) | `handleCircuitOpen()` runs: logs warning, events stay PENDING |
| **Half-Open** (recovering) | 3 trial publishes; if any succeeds → close, else → re-open |

### Testing

- **OutboxPublisherTest**: mock `RabbitTemplate` to throw `AmqpException` → verify fallback is called, event stays PENDING
- **OutboxPublisherTest**: mock success → verify event marked PUBLISHED
- **Integration test**: verify circuit opens after configurable failures, closes after recovery

### What does NOT change

- `PolicyController` — no new imports, no circuit awareness
- `PolicyService` — unchanged
- API contract — `202 Accepted` remains the same
- Event safety — outbox table preserves all events regardless of circuit state
- Existing tests — all continue passing
