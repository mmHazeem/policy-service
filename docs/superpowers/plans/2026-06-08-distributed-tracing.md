# Distributed Tracing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development

**Goal:** Add distributed tracing with OpenTelemetry so the async HTTP→Outbox→RabbitMQ→Listener→DB flow shares a single traceId.

**Architecture:** Micrometer Tracing bridge to OpenTelemetry, exporting spans to Zipkin. Spring Boot 3.4 auto-instruments RabbitTemplate and @RabbitListener — no manual span creation needed.

**Tech Stack:** Micrometer Tracing, OpenTelemetry, Zipkin, Spring Boot 3.4, RabbitMQ

---

### Task 1: Add dependencies to pom.xml

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Add Micrometer Tracing bridge and Zipkin exporter**

```xml
<!-- Tracing -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

Place after the `micrometer-registry-prometheus` dependency block.

### Task 2: Add tracing config to application.yaml

**Files:**
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Add tracing config under management**

```yaml
management:
  tracing:
    sampling.probability: 1.0
  zipkin:
    tracing.endpoint: http://zipkin:9411/api/v2/spans
```

Add after the existing `management.endpoint` block.

### Task 3: Add Zipkin to docker-compose.yml

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add Zipkin service**

```yaml
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"
    networks:
      - monitor-net
```

Place after the Grafana service block, before the `networks:` section.

### Task 4: Verify compilation and test

- [ ] **Step 1: Compile and run unit tests**

```bash
mvn test -pl . -Dtest="PolicyServiceTest,PolicyControllerTest" -DfailIfNoTests=false
```

Expected: BUILD SUCCESS, 21 tests pass
