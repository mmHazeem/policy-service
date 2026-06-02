# policy-service
# Policy Service

[![Java CI with Maven](https://github.com/mmHazeem/policy-service/actions/workflows/ci.yml/badge.svg)](https://github.com/mmHazeem/policy-service/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green?logo=springboot)](https://spring.io/projects/spring-boot)

A production-ready **insurance policy management microservice** built with Spring Boot 3, demonstrating asynchronous event-driven architecture, JWT-secured REST APIs, Redis caching, and full observability through Prometheus and Grafana.

---

## Architecture Overview

```
Client
  │
  ▼
┌─────────────────────────────┐
│  REST API  (Spring MVC)     │  JWT Authentication
│  POST /api/v1/policies      │──────────────────────► 202 Accepted (non-blocking)
│  GET  /api/v1/policies      │
└────────────┬────────────────┘
             │ publish
             ▼
┌────────────────────────┐     retry (×3 + backoff)    ┌──────────┐
│   RabbitMQ Exchange    │──────────────────────────── ► Dead-Letter│
│   policy.exchange      │                              │  Queue   │
└────────────┬───────────┘                              └──────────┘
             │ consume
             ▼
┌────────────────────────┐     cache evict    ┌─────────────────┐
│   PolicyMessageListener │──────────────────► │  Redis Cache    │
│   (idempotent)          │                   │  @Cacheable     │
└────────────┬────────────┘                   └─────────────────┘
             │ save
             ▼
┌────────────────────────┐
│     PostgreSQL          │  Flyway migrations
│     policies table      │
└────────────────────────┘
             │ metrics
             ▼
┌────────────────────────┐     scrape     ┌─────────────────┐
│  Spring Actuator        │──────────────► │   Prometheus    │
│  /actuator/prometheus   │               └────────┬────────┘
└────────────────────────┘                        │ visualize
                                                  ▼
                                         ┌─────────────────┐
                                         │    Grafana      │
                                         └─────────────────┘
```

---

## Features

- **Async policy creation** — controller publishes to RabbitMQ and returns `202 Accepted` immediately; listener persists asynchronously
- **JWT authentication** — stateless security via `Authorization: Bearer <token>`; register and login via `/api/v1/auth`
- **Idempotent processing** — duplicate policy numbers are rejected without retry, preventing DLQ noise
- **Dead-letter queue** — failed messages retry 3× with exponential backoff, then route to `policy.queue.dlq`
- **Redis caching** — `GET /policies/{id}` is cached; cache evicted on any write
- **Full observability** — custom Micrometer counters exposed at `/actuator/prometheus`; Grafana dashboard included
- **Integration tested** — Testcontainers spins up real Postgres, RabbitMQ, and Redis for every test run

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 (Records, Sealed classes, Virtual Threads ready) |
| Framework | Spring Boot 3.4 |
| API | Spring MVC + SpringDoc OpenAPI (Swagger UI) |
| Security | Spring Security + JWT (jjwt 0.12) |
| Persistence | Spring Data JPA + PostgreSQL + Flyway |
| Messaging | Spring AMQP + RabbitMQ |
| Caching | Spring Cache + Redis |
| Observability | Micrometer + Prometheus + Grafana |
| Mapping | MapStruct + Lombok |
| Testing | JUnit 5 + Mockito + Testcontainers + Awaitility |
| CI | GitHub Actions |

---

## Prerequisites

- **Docker** (required — used for local infrastructure and Testcontainers tests)
- **Java 21**
- **Maven 3.9+** (or use the included `./mvnw` wrapper)

---

## Running Locally

**1. Start all infrastructure (Postgres, RabbitMQ, Redis, Prometheus, Grafana):**

```bash
docker compose up -d
```

**2. Run the application:**

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8081`.

**3. Open the Swagger UI:**

```
http://localhost:8081/swagger-ui/index.html
```

| Service | URL | Credentials |
|---|---|---|
| Swagger UI | http://localhost:8081/swagger-ui/index.html | — |
| RabbitMQ Management | http://localhost:15672 | guest / guest |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | — |

---

## API Reference

### Authentication

All `/api/v1/policies` endpoints require `Authorization: Bearer <token>`.

**Register**
```http
POST /api/v1/auth/register
Content-Type: application/json

{ "username": "admin", "password": "password123" }
```

**Login**
```http
POST /api/v1/auth/login
Content-Type: application/json

{ "username": "admin", "password": "password123" }
```

Both return:
```json
{ "token": "eyJhbGciOiJIUzI1NiJ9..." }
```

---

### Policies

**Create a policy** (async — returns immediately)
```http
POST /api/v1/policies
Authorization: Bearer <token>
Content-Type: application/json

{
  "policyNumber": "POL-001",
  "policyHolder": "Jane Doe",
  "coverageAmount": 50000.00,
  "startDate": "2026-01-01"
}
```
Response: `202 Accepted`

**List all policies**
```http
GET /api/v1/policies
Authorization: Bearer <token>
```

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/insurance_db` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `root` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `JWT_SECRET` | *(default in config)* | Base64-encoded HMAC-SHA256 key — **always override in production** |

---

## Running Tests

Ensure Docker is running, then:

```bash
./mvnw verify
```

Testcontainers automatically provisions isolated **Postgres**, **RabbitMQ**, and **Redis** containers for the test run — no manual setup required. The test suite includes:

- **Unit tests** — service logic, premium calculation, idempotency, metrics
- **Controller slice tests** — `@WebMvcTest` with JWT security, validation, 401 flows
- **Integration tests** — full async flow, DLQ retry exhaustion, JWT authentication end-to-end
- **Smoke tests** — context load verification for all critical beans

---

## Project Structure

```
src/
├── main/java/com/insurance/policy/
│   ├── config/          # SecurityConfig, OpenApiConfig
│   ├── domain/          # Policy, User (JPA entities)
│   ├── dtos/            # PolicyRequest, PolicyResponse, AuthRequest, AuthResponse
│   ├── exception/       # GlobalExceptionHandler, custom exceptions
│   ├── Listener/        # RabbitMQConfig, PolicyMessageListener (async consumer)
│   ├── mapper/          # PolicyMapper (MapStruct)
│   ├── policy_service/  # PolicyService
│   ├── repository/      # PolicyRepository, UserRepository
│   ├── security/        # JwtService, JwtAuthenticationFilter, AuthService, UserDetailsServiceImpl
│   └── web/             # PolicyController, AuthController
├── main/resources/
│   ├── application.yaml
│   └── db/migration/    # V1__init_schema.sql, V2__users_table.sql
└── test/
    ├── java/com/insurance/policy/
    │   ├── BaseContainerTest.java       # Shared Testcontainers setup
    │   ├── BaseIntegrationTest.java     # JWT helpers + TestRestTemplate
    │   └── policy_service/             # All test classes
    └── resources/
        └── application.yaml            # Test overrides (Flyway off, create-drop)
```

---

## CI/CD

Every push to `main` triggers the GitHub Actions pipeline which:

1. Sets up Java 21 (Temurin)
2. Verifies Docker is available for Testcontainers
3. Runs `./mvnw verify` — compiles, runs all unit and integration tests
4. Uploads Surefire reports as an artifact on failure

See [`.github/workflows/ci.yml`](.github/workflows/ci.yml).