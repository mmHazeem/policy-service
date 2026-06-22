# S3 Document Storage for Policy Service

## Overview

Add policy document upload capability using S3-compatible storage (LocalStack in dev, real S3 in production). Documents are uploaded synchronously after policy creation, completely decoupled from the async outbox/RabbitMQ flow.

## Design

### Flow

```
Client → POST /api/v1/policies/{policyId}/documents (multipart/form-data)
          → DocumentService.upload(policyId, file)
              1. Verify policy exists (policyRepository.findById)
              2. Upload file to S3 (policies/{policyId}/{uuid})
              3. Save Document row with policyId FK
          → 201 Created
```

- **Synchronous** — no outbox, no async listener involved
- **S3-first, DB-second** — if DB save fails after S3 upload, the orphaned object is harmless and cleanable; the reverse would leave a broken Document record
- **UUID-only S3 keys** — no raw user filenames in keys (spaces, unicode, path traversal, encoding issues avoided)
  - S3 key format: `policies/{policyId}/{uuid}`

### Endpoints

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| `POST` | `/api/v1/policies/{policyId}/documents` | `ROLE_USER` | Upload document |
| `GET` | `/api/v1/policies/{policyId}/documents` | `ROLE_USER` | List documents for policy |
| `DELETE` | `/api/v1/documents/{documentId}` | `ROLE_ADMIN` | Delete document |

### Domain Model

```sql
CREATE TABLE policy_documents (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES policies(id),
    file_name VARCHAR(255) NOT NULL,       -- original filename (metadata only)
    s3_key VARCHAR(512) NOT NULL,           -- policies/{policyId}/{uuid}
    content_type VARCHAR(127),
    file_size BIGINT NOT NULL,              -- bytes, BIGINT for large PDFs
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

`Document` entity: `@Entity`, `@ManyToOne(fetch = LAZY)` → `Policy`, standard getters/constructors.

`DocumentRepository`: extends `JpaRepository<Document, UUID>`, methods:
- `findByPolicyIdOrderByUploadedAtDesc(UUID policyId): List<Document>`

### DTOs

```java
public record DocumentResponse(
    UUID id,
    UUID policyId,
    String fileName,
    String contentType,
    long fileSize,
    Instant uploadedAt
) {}
```

No request DTO — `@RequestParam("file") MultipartFile` suffices.

### Infrastructure

**Docker Compose** — add LocalStack to existing `docker-compose.yml`:

```yaml
localstack:
  image: localstack/localstack:latest
  ports:
    - "4566:4566"
  environment:
    SERVICES: secretsmanager,sqs,cloudwatch,s3
    AWS_DEFAULT_REGION: eu-central-1
  volumes:
    -     "./localstack/init-aws.sh:/etc/localstack/init/ready.d/init-aws.sh"
```

**Init script** (`localstack/init-aws.sh`):

```bash
#!/bin/bash
awslocal s3 mb s3://insurance-documents
```

### Configuration

No `aws.s3.*` in main `application.yaml` — production uses IAM role / default credential chain with no endpoint override.

**`src/main/resources/application-local.yaml`** (new):
```yaml
aws:
  s3:
    endpoint: http://localhost:4566
    region: eu-central-1
    bucket: insurance-documents
```

**`src/main/resources/application-docker.yaml`** (new — for Docker Compose app container):
```yaml
aws:
  s3:
    endpoint: http://localstack:4566
    region: eu-central-1
    bucket: insurance-documents
```

**`src/test/resources/application.yaml`** — add the same keys, or let `@DynamicPropertySource` override from Testcontainers LocalStack.

### Dependencies

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

Spring Boot manages the BOM via `spring-boot-dependencies`.

### Beans

```java
@Configuration
public class AwsConfig {

    @Value("${aws.s3.endpoint:#{null}}")
    private String endpoint;

    @Value("${aws.s3.region:eu-central-1}")
    private String region;

    @Value("${aws.s3.bucket:insurance-documents}")
    private String bucket;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder().region(Region.of(region));
        if (endpoint != null) {
            builder.endpointOverride(URI.create(endpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("dummy", "dummy")))
                   .forcePathStyle(true);
        }
        return builder.build();
    }

    @Bean
    public String s3Bucket() {
        return bucket;
    }
}
```

- `endpoint` is `null` in production → default SDK behavior (IAM role, standard endpoint)
- `endpoint` is set in local/docker/test profiles → LocalStack mode with static creds + path-style

### Multipart Config

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

### Service

```java
@Service
public class DocumentService {

    private final PolicyRepository policyRepository;
    private final DocumentRepository documentRepository;
    private final S3Client s3Client;
    private final String bucket;

    public DocumentResponse upload(UUID policyId, MultipartFile file) {
        Policy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));

        UUID docId = UUID.randomUUID();
        String s3Key = "policies/%s/%s".formatted(policyId, docId);

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Key)
                        .contentType(file.getContentType())
                        .contentLength(file.getSize())
                        .build(),
                RequestBody.fromBytes(file.getBytes()));

        Document document = new Document(docId, policy, file.getOriginalFilename(),
                s3Key, file.getContentType(), file.getSize(), Instant.now());
        documentRepository.save(document);

        return toResponse(document);
    }

    public List<DocumentResponse> getDocuments(UUID policyId) {
        return documentRepository.findByPolicyIdOrderByUploadedAtDesc(policyId)
                .stream().map(this::toResponse).toList();
    }

    public void deleteDocument(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket).key(doc.getS3Key()).build());
        documentRepository.delete(doc);
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(doc.getId(), doc.getPolicy().getId(),
                doc.getFileName(), doc.getContentType(), doc.getFileSize(), doc.getUploadedAt());
    }
}
```

### Error Handling

| Scenario | HTTP | Behavior |
|----------|------|----------|
| Policy not found | 404 | `PolicyNotFoundException` → `GlobalExceptionHandler` |
| S3 upload fails | 500 | `DocumentUploadException` (unchecked), no DB insert |
| File too large | 413 | Spring `MaxUploadSizeExceededException` → handled |
| Document not found | 404 | `DocumentNotFoundException` → handler |

### Testing

**Unit tests** (`@ExtendWith(MockitoExtension.class)`):
- `DocumentServiceTest` — mock `S3Client`, `PolicyRepository`, `DocumentRepository`
- `DocumentControllerTest` — `@WebMvcTest(DocumentController.class)`, `@MockBean DocumentService`

**Integration tests** — extend `BaseIntegrationTest`:
- `DocumentIntegrationTest` — Testcontainers LocalStack (`new LocalStackContainer("3.0.2").withServices(S3)`), verifies full upload → S3 → DB → GET flow

**BaseContainerTest** — add LocalStack alongside existing PG/RabbitMQ/Redis containers

### Migration

```sql
-- V5__create_documents_table.sql
CREATE TABLE policy_documents (
    id UUID PRIMARY KEY,
    policy_id UUID NOT NULL REFERENCES policies(id),
    file_name VARCHAR(255) NOT NULL,
    s3_key VARCHAR(512) NOT NULL,
    content_type VARCHAR(127),
    file_size BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_policy_documents_policy_id ON policy_documents(policy_id);
```

## Out of Scope

- Presigned URLs for download (can be added later)
- Document versions / multiple revisions
- Bulk upload
- Async document processing (virus scanning, OCR)
- S3 event notifications
