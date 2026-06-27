# policy-service — AGENTS.md

## Commands

| Action | Command |
|--------|---------|
| Run all tests | `./mvnw verify` (requires Docker for Testcontainers) |
| Run one test | `mvn test -Dtest="DocumentServiceTest"` |
| Mutation coverage | `mvn pitest:mutationCoverage -Ppitest -DtargetTests="com.insurance.policy.policy_service.*Test,com.insurance.policy.exception.*Test,com.insurance.policy.service.*Test,com.insurance.policy.web.*Test,com.insurance.policy.domain.*Test"` |
| Run app (local) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (starts on port 8081) |
| Run app (docker) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=docker` |
| Start infra | `docker compose up -d` (Postgres, RabbitMQ, Redis, LocalStack, Prometheus, Grafana) |
| Terraform plan | `cd infra/terraform && terraform plan` |
| Terraform apply | `cd infra/terraform && terraform apply` |
| Terraform destroy | `cd infra/terraform && terraform destroy` |

Testcontainers auto-provisions Postgres/RabbitMQ/Redis/LocalStack — no manual setup. CI sets `TESTCONTAINERS_CHECKS_DISABLE=true` and `JWT_SECRET`.

## Architecture

- **Dual creation paths**: `POST /api/v1/policies` → `createPolicyAsync` → outbox → RabbitMQ → `PolicyMessageListener` → DB (returns 202). Direct `createPolicy` used by the listener for actual persistence.
- **Outbox pattern**: `OutboxPublisher` polls `PENDING` events every 5s, publishes to RabbitMQ. Has `@CircuitBreaker(name="rabbitmq")` with retry logic (up to `maxRetries`).
- **RabbitMQ**: Exchange `policy.exchange.final`, queue `policy.queue.final`, DLQ `policy.queue.dlq.final`. Retry 3× with exponential backoff (1s, 2x, max 10s), then DLQ.
- **Status machine**: `DRAFT → ACTIVE → CANCELLED`. Only `ADMIN` role can update status.
- **Cache**: `getPolicyById` is `@Cacheable("policies")`. All write methods `@CacheEvict(allEntries=true)`.
- **Correlation ID**: `CorrelationIdFilter` (before `JwtAuthenticationFilter`) propagates HTTP header → MDC → outbox event → RabbitMQ message header → listener MDC.
- **S3 documents**: `DocumentService.upload()` writes to S3 first, then DB (S3-first ordering). `GET /api/v1/documents/{id}/download` returns a pre-signed S3 URL (15 min expiry) — files never stream through the app. `@PreAuthorize` controls access (`hasRole('USER')` for upload/list/download, `hasRole('ADMIN')` for delete).
- **AwsConfig**: `s3Client()` and `s3Presigner()` beans use `@Value("${aws.s3.endpoint:#{null}}")` — null skips LocalStack (production uses IAM role), non-null enables `forcePathStyle` with dummy credentials. `S3Presigner` uses `serviceConfiguration()` instead of `forcePathStyle()` (not available on `S3Presigner.Builder`).
- **RBAC**: `@EnableMethodSecurity` + `@PreAuthorize` on controller methods. `AuthService.register()` assigns `USER` role by default; `ADMIN` only via DB.
- **Observability**: Micrometer counter `insurance.policies.created`, Prometheus at `/actuator/prometheus`, tracing via Zipkin.

## Project layout

```
config/     → SecurityConfig, JwtAuthenticationFilter, JwtService, CorrelationIdFilter, AwsConfig, AuditConfig, OpenApiConfig, AuthService
domain/     → Policy, User, OutboxEvent, Document, BaseEntity (JPA audit fields)
dtos/       → Records: PolicyRequest/Response, AuthRequest/Response, PageResponse, PolicyStatusRequest, DocumentResponse, UserResponse
exception/  → GlobalExceptionHandler + ApiError, PolicyNotFound, PolicyAlreadyExists, InvalidTransition, DocumentNotFound, DocumentUpload
Listener/   → RabbitMQConfig (DLQ wiring), PolicyMessageListener (async consumer)
mapper/     → PolicyMapper (MapStruct), UserMapper
outbox/     → OutboxPublisher (scheduled + circuit-breaker)
policy_service/ → PolicyService, UserDetailsServiceImpl
repository/ → PolicyRepository, UserRepository, OutboxRepository, DocumentRepository
service/    → DocumentService (S3 + JPA)
web/        → PolicyController, AuthController, DocumentController
```

## Testing conventions

- **Unit tests** use `@ExtendWith(MockitoExtension.class)` with Mockito — no Spring context.
- **Web slice tests** use `@WebMvcTest` with `@MockBean` for services.
- **Integration tests** extend `BaseIntegrationTest` → `BaseContainerTest` (spins up Postgres, RabbitMQ, Redis, LocalStack S3 via Testcontainers). `BaseContainerTest` has `@BeforeAll static createS3Bucket()`.
- **Architecture tests** (`architecture/ArchitectureTest`) use ArchUnit for layer isolation, naming conventions, annotation presence — run as part of `verify`.
- **Test config**: Flyway disabled, `ddl-auto: validate` (no migrations needed).
- **PIT**: targetTests must include new test packages explicitly. Integration tests may cause PIT minion timeouts — use `-DtargetTests` to scope.

## Gotchas

- `PropertyReferenceException` constructor requires `TypeInformation<?>` not `Class<?>` — use `ClassTypeInformation.from(Policy.class)` in tests.
- `ApiError` uses `@Data` + `@Builder` — all-args constructor is package-private. Use `ApiError.builder()...build()` in tests outside the package.
- `PolicyService.createPolicy` uses constructor injection with 5 params; tests must supply all five.
- `Policy` entity has `@GeneratedValue(strategy = GenerationType.UUID)` — never manually set ID before `save()` in tests or the entity will error.
- `Document` entity uses Lombok `@Builder` + `@Id` set manually (UUID). `documentRepository.findByPolicyIdOrderByUploadedAtDesc(UUID)`.
- MapStruct mapper implementations are generated at compile time — no source in repo.
- `UserResponse` is a record: `(UUID uuid, String username, User.Role role)`.
- `DocumentResponse` is a record: `(UUID id, UUID policyId, String fileName, String contentType, long fileSize, Instant uploadedAt)`.
- Flyway in production (`ddl-auto: create` + Flyway `enabled: true`); in tests (`ddl-auto: create-drop` + Flyway `enabled: false`).
- Branch naming convention: `feat/feature-name`.
- Profile-specific yamls: `application-local.yaml` (LocalStack at `localhost:4566`), `application-docker.yaml` (LocalStack at `localstack:4566`). Main `application.yaml` has no `aws.s3.*` — production uses IAM role.
