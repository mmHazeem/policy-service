# Structured Logging + Correlation ID

## Problem

Every subsystem logs independently with no common identifier. An HTTP POST to `/api/v1/policies` writes log lines, the outbox scheduler writes its own, and the RabbitMQ consumer writes separate lines. When a production issue hits, you can't trace a single request across HTTP → outbox → RabbitMQ → consumer. Each hop is a silo.

Additionally, logs are plain text with no structured fields — grep-only, no indexed field queries, no machine-parseable output.

## Constraint

Must not break the outbox pattern's async decoupling. The correlation ID must survive the database persistence boundary (outbox table) and the messaging boundary (RabbitMQ headers). No new runtime dependencies beyond a lightweight logging encoder.

## Solution

### Data flow

```
HTTP Request (with X-Correlation-Id or generated UUID)
    │
    ├─ CorrelationIdFilter
    │   ├─ Reads X-Correlation-Id header or generates UUID
    │   ├─ Sets MDC["correlationId"]
    │   ├─ Sets response header X-Correlation-Id
    │   └─ Clears MDC in finally
    │
    ├─ PolicyService.createPolicyAsync()
    │   └─ Stores correlationId in OutboxEvent.correlationId column
    │
    ├─ OutboxPublisher (scheduled, different thread)
    │   ├─ Reads correlationId from OutboxEvent
    │   ├─ Sets MDC["correlationId"]
    │   └─ Adds correlationId as RabbitMQ message header
    │
    └─ PolicyMessageListener (consumer thread)
        ├─ Extracts correlationId from RabbitMQ headers
        ├─ Sets MDC["correlationId"]
        ├─ Processes message
        └─ Clears MDC
```

Every log line across the entire flow includes `correlationId` in JSON structured output.

### Component changes

**pom.xml** — add `net.logstash.logback:logstash-logback-encoder` for JSON encoding.

**logback-spring.xml** (new) — configure `LoggingEventCompositeJsonEncoder` with MDC fields, including `correlationId`, `level`, `logger`, `thread`, `message`, `timestamp`.

**CorrelationIdFilter** (new, `OncePerRequestFilter`) — extract or generate correlation ID, manage MDC lifecycle, set response header.

**SecurityConfig** — register `CorrelationIdFilter` after `JwtAuthenticationFilter`.

**OutboxEvent entity** — add `correlationId` field (nullable, populated during outbox event creation).

**V4__add_correlation_id.sql** (new migration) — `ALTER TABLE outbox_events ADD COLUMN correlation_id VARCHAR(36)`.

**PolicyService.createPolicyAsync()** — read `MDC.get("correlationId")`, fallback to `UUID.randomUUID()` if null, set on `OutboxEvent`.

**OutboxPublisher** — read `outboxEvent.getCorrelationId()`, set `MDC.put("correlationId", ...)` before publishing, add as `MessagePostProcessor` header, clear MDC in finally.

**PolicyMessageListener** — accept `@Header("correlationId")` parameter, set MDC, clear in finally.

### What does NOT change

- API request/response shapes (`PolicyRequest`, `PolicyResponse`)
- Outbox retry logic
- Circuit breaker configuration
- Existing tests — all continue passing
- Any database indexes beyond the new column
