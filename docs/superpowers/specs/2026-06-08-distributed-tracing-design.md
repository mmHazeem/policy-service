# Distributed Tracing with OpenTelemetry

## Problem

The async flow (HTTP POST → Outbox → RabbitMQ → Listener → DB) produces logs with no correlation between the incoming request and downstream processing. Debugging failures requires manual log spelunking across services.

## Solution

Add Micrometer Tracing with OpenTelemetry bridge + Zipkin exporter. Spring Boot 3.4 auto-configures RabbitTemplate and @RabbitListener instrumentation — trace context propagates through message headers automatically.

## Design

**Dependencies (pom.xml):**

| Dependency | Purpose |
|------------|---------|
| `micrometer-tracing-bridge-otel` | Bridges Micrometer Tracing API to OpenTelemetry |
| `opentelemetry-exporter-zipkin` | Sends spans to Zipkin |

**Config (application.yaml):**
```yaml
management:
  tracing:
    sampling.probability: 1.0
  zipkin:
    tracing.endpoint: http://zipkin:9411/api/v2/spans
```

**Infrastructure (docker-compose.yml):**
- Add Zipkin service (`openzipkin/zipkin:latest`, port 9411)
- Already on `monitor-net` network

**No Java code changes needed.** Spring Boot auto-configures:
- `RabbitTemplate` instrumentation — injects trace headers (`traceparent`) into outbound messages
- `@RabbitListener` instrumentation — extracts trace from inbound message headers
- `RestTemplate`/`WebClient` instrumentation — but not used in this flow
- Scheduled task tracing — `OutboxPublisher.publishPendingEvents()` gets spans

## Trace Flow

```
HTTP POST /api/v1/policies          ← traceId: abc123
  └─ OutboxPublisher (scheduled)    ← same traceId (propagated via DB outbox)
      └─ rabbitTemplate.send()      ← injects traceparent header
          └─ PolicyMessageListener  ← extracts traceparent, continues trace
              └─ PolicyService.createPolicy()
                  └─ DB INSERT
```

All spans share the same `traceId` in Zipkin at `http://localhost:9411`.
