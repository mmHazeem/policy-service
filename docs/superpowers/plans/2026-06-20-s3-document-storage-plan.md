# S3 Document Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add S3-backed policy document upload capability with LocalStack for local development and Testcontainers for tests.

**Architecture:** Synchronous multipart upload to a new `POST /api/v1/policies/{policyId}/documents` endpoint. Files upload to S3 first, then a `Document` row is persisted with FK to `policies`. No changes to the existing async outbox/RabbitMQ flow. S3 config is profile-activated — production uses default SDK chain (IAM role), local/docker/test profiles override endpoint to LocalStack.

**Tech Stack:** AWS SDK S3 v2, LocalStack, Testcontainers (localstack/3.0.2), Spring multipart

---

### Task 1: Infrastructure — Docker Compose, Init Script, Dependencies, Profile Config, Bean Config

**Files:**
- Modify: `docker-compose.yml`
- Create: `localstack/init-aws.sh`
- Create: `src/main/java/com/insurance/policy/config/AwsConfig.java`
- Create: `src/main/resources/application-local.yaml`
- Create: `src/main/resources/application-docker.yaml`
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`

- [ ] **Step 1: Add LocalStack to docker-compose.yml**

Add after the `zipkin` service block:

```yaml
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      SERVICES: secretsmanager,sqs,cloudwatch,s3
      AWS_DEFAULT_REGION: eu-central-1
    volumes:
      - "./localstack/init-aws.sh:/etc/localstack/init/ready.d/init-aws.sh"
```

- [ ] **Step 2: Create LocalStack init script**

Create `localstack/init-aws.sh`:

```bash
#!/bin/bash
awslocal s3 mb s3://insurance-documents
```

Make it executable: `chmod +x localstack/init-aws.sh`

- [ ] **Step 3: Add S3 dependency to pom.xml**

Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
```

- [ ] **Step 4: Add multipart config to application.yaml**

Add to `src/main/resources/application.yaml`:

```yaml
spring:
  # ... existing config ...
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

- [ ] **Step 5: Create local profile config**

Create `src/main/resources/application-local.yaml`:

```yaml
aws:
  s3:
    endpoint: http://localhost:4566
    region: eu-central-1
    bucket: insurance-documents
```

- [ ] **Step 6: Create docker profile config**

Create `src/main/resources/application-docker.yaml`:

```yaml
aws:
  s3:
    endpoint: http://localstack:4566
    region: eu-central-1
    bucket: insurance-documents
```

- [ ] **Step 7: Create AwsConfig**

Create `src/main/java/com/insurance/policy/config/AwsConfig.java`:

```java
package com.insurance.policy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

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

- [ ] **Step 8: Commit**

```bash
git add docker-compose.yml localstack/init-aws.sh pom.xml \
  src/main/resources/application.yaml \
  src/main/resources/application-local.yaml \
  src/main/resources/application-docker.yaml \
  src/main/java/com/insurance/policy/config/AwsConfig.java
git commit -m "feat: add LocalStack S3 infrastructure and config"
```

---

### Task 2: Flyway Migration — policy_documents Table

**Files:**
- Create: `src/main/resources/db/migration/V5__create_documents_table.sql`

- [ ] **Step 1: Write the migration**

Create `src/main/resources/db/migration/V5__create_documents_table.sql`:

```sql
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

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/db/migration/V5__create_documents_table.sql
git commit -m "feat: add policy_documents table"
```

---

### Task 3: Domain Entity + Repository

**Files:**
- Create: `src/main/java/com/insurance/policy/domain/Document.java`
- Create: `src/main/java/com/insurance/policy/repository/DocumentRepository.java`

- [ ] **Step 1: Write the test**

Create `src/test/java/com/insurance/policy/domain/DocumentTest.java`:

```java
package com.insurance.policy.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {

    @Test
    void shouldCreateDocument() {
        var policy = new Policy();
        policy.setId(UUID.randomUUID());
        var id = UUID.randomUUID();
        var now = Instant.now();

        var doc = new Document(id, policy, "policy.pdf",
                "policies/" + policy.getId() + "/" + id,
                "application/pdf", 1024L, now);

        assertEquals(id, doc.getId());
        assertEquals(policy.getId(), doc.getPolicy().getId());
        assertEquals("policy.pdf", doc.getFileName());
        assertEquals(1024L, doc.getFileSize());
        assertEquals(now, doc.getUploadedAt());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest="DocumentTest" -DfailIfNoTests=false`
Expected: Compilation error — `Document` class not found.

- [ ] **Step 3: Create Document entity**

Create `src/main/java/com/insurance/policy/domain/Document.java`:

```java
package com.insurance.policy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy_documents")
public class Document {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected Document() {}

    public Document(UUID id, Policy policy, String fileName, String s3Key,
                    String contentType, Long fileSize, Instant uploadedAt) {
        this.id = id;
        this.policy = policy;
        this.fileName = fileName;
        this.s3Key = s3Key;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() { return id; }
    public Policy getPolicy() { return policy; }
    public String getFileName() { return fileName; }
    public String getS3Key() { return s3Key; }
    public String getContentType() { return contentType; }
    public Long getFileSize() { return fileSize; }
    public Instant getUploadedAt() { return uploadedAt; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest="DocumentTest" -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 5: Create DocumentRepository**

Create `src/main/java/com/insurance/policy/repository/DocumentRepository.java`:

```java
package com.insurance.policy.repository;

import com.insurance.policy.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findByPolicyIdOrderByUploadedAtDesc(UUID policyId);
}
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/insurance/policy/domain/Document.java \
  src/main/java/com/insurance/policy/repository/DocumentRepository.java \
  src/test/java/com/insurance/policy/domain/DocumentTest.java
git commit -m "feat: add Document entity and repository"
```

---

### Task 4: DTOs + Custom Exceptions + Global Handler

**Files:**
- Create: `src/main/java/com/insurance/policy/dtos/DocumentResponse.java`
- Create: `src/main/java/com/insurance/policy/exception/DocumentNotFoundException.java`
- Create: `src/main/java/com/insurance/policy/exception/DocumentUploadException.java`
- Modify: `src/main/java/com/insurance/policy/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create DocumentResponse DTO**

Create `src/main/java/com/insurance/policy/dtos/DocumentResponse.java`:

```java
package com.insurance.policy.dtos;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID policyId,
    String fileName,
    String contentType,
    long fileSize,
    Instant uploadedAt
) {}
```

- [ ] **Step 2: Create DocumentNotFoundException**

Create `src/main/java/com/insurance/policy/exception/DocumentNotFoundException.java`:

```java
package com.insurance.policy.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID documentId) {
        super("Document not found with id: " + documentId);
    }
}
```

- [ ] **Step 3: Create DocumentUploadException**

Create `src/main/java/com/insurance/policy/exception/DocumentUploadException.java`:

```java
package com.insurance.policy.exception;

public class DocumentUploadException extends RuntimeException {
    public DocumentUploadException(String message, Throwable cause) {
        super("Failed to upload document: " + message, cause);
    }
}
```

- [ ] **Step 4: Register exceptions in GlobalExceptionHandler**

Read `src/main/java/com/insurance/policy/exception/GlobalExceptionHandler.java` first to find existing pattern, then add:

Before the closing `}` of the class, add:

```java
@ExceptionHandler(DocumentNotFoundException.class)
public ResponseEntity<ApiError> handleDocumentNotFound(DocumentNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.builder()
                    .status(HttpStatus.NOT_FOUND.value())
                    .message(ex.getMessage())
                    .build());
}

@ExceptionHandler(DocumentUploadException.class)
public ResponseEntity<ApiError> handleDocumentUpload(DocumentUploadException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .message(ex.getMessage())
                    .build());
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/policy/dtos/DocumentResponse.java \
  src/main/java/com/insurance/policy/exception/DocumentNotFoundException.java \
  src/main/java/com/insurance/policy/exception/DocumentUploadException.java \
  src/main/java/com/insurance/policy/exception/GlobalExceptionHandler.java
git commit -m "feat: add DocumentResponse DTO and document exceptions"
```

---

### Task 5: DocumentService

**Files:**
- Create: `src/main/java/com/insurance/policy/service/DocumentService.java`
- Create: `src/test/java/com/insurance/policy/service/DocumentServiceTest.java`

- [ ] **Step 1: Write the unit test**

Create `src/test/java/com/insurance/policy/service/DocumentServiceTest.java`:

```java
package com.insurance.policy.service;

import com.insurance.policy.domain.Document;
import com.insurance.policy.domain.Policy;
import com.insurance.policy.dtos.DocumentResponse;
import com.insurance.policy.exception.DocumentNotFoundException;
import com.insurance.policy.exception.DocumentUploadException;
import com.insurance.policy.exception.PolicyNotFoundException;
import com.insurance.policy.repository.DocumentRepository;
import com.insurance.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void shouldUploadDocument() throws Exception {
        var policyId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(policyId);
        var file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "test content".getBytes());

        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));

        var docCaptor = ArgumentCaptor.forClass(Document.class);
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = documentService.upload(policyId, file);

        assertNotNull(response.id());
        assertEquals(policyId, response.policyId());
        assertEquals("policy.pdf", response.fileName());
        assertEquals("application/pdf", response.contentType());
        assertEquals(file.getSize(), response.fileSize());

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void shouldThrowPolicyNotFoundExceptionWhenPolicyDoesNotExist() {
        var policyId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());

        when(policyRepository.findById(policyId)).thenReturn(Optional.empty());

        assertThrows(PolicyNotFoundException.class, () -> documentService.upload(policyId, file));
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldThrowDocumentUploadExceptionWhenS3Fails() throws Exception {
        var policyId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(policyId);
        var file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());

        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));

        assertThrows(DocumentUploadException.class, () -> documentService.upload(policyId, file));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldUploadToS3WithCorrectKey() throws Exception {
        var policyId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(policyId);
        var file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "data".getBytes());

        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = documentService.upload(policyId, file);

        assertTrue(response.fileName().startsWith("policies/" + policyId + "/"));
    }

    @Test
    void shouldListDocumentsByPolicyId() {
        var policyId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(policyId);
        var doc = new Document(UUID.randomUUID(), policy, "a.pdf", "key", "pdf", 100L, Instant.now());

        when(documentRepository.findByPolicyIdOrderByUploadedAtDesc(policyId))
                .thenReturn(List.of(doc));

        var docs = documentService.getDocuments(policyId);

        assertEquals(1, docs.size());
        assertEquals("a.pdf", docs.getFirst().fileName());
    }

    @Test
    void shouldDeleteDocument() {
        var docId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(UUID.randomUUID());
        var doc = new Document(docId, policy, "a.pdf", "s3/key", "pdf", 100L, Instant.now());

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        documentService.deleteDocument(docId);

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        verify(documentRepository).delete(doc);
    }

    @Test
    void shouldThrowDocumentNotFoundExceptionWhenDeletingNonExistent() {
        var docId = UUID.randomUUID();
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> documentService.deleteDocument(docId));
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(documentRepository, never()).delete(any());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest="DocumentServiceTest" -DfailIfNoTests=false`
Expected: Compilation error — `DocumentService` not found.

- [ ] **Step 3: Create DocumentService**

Create `src/main/java/com/insurance/policy/service/DocumentService.java`:

```java
package com.insurance.policy.service;

import com.insurance.policy.domain.Document;
import com.insurance.policy.dtos.DocumentResponse;
import com.insurance.policy.exception.DocumentNotFoundException;
import com.insurance.policy.exception.DocumentUploadException;
import com.insurance.policy.exception.PolicyNotFoundException;
import com.insurance.policy.repository.DocumentRepository;
import com.insurance.policy.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final PolicyRepository policyRepository;
    private final DocumentRepository documentRepository;
    private final S3Client s3Client;
    private final String s3Bucket;

    public DocumentService(PolicyRepository policyRepository,
                           DocumentRepository documentRepository,
                           S3Client s3Client,
                           String s3Bucket) {
        this.policyRepository = policyRepository;
        this.documentRepository = documentRepository;
        this.s3Client = s3Client;
        this.s3Bucket = s3Bucket;
    }

    @Transactional
    public DocumentResponse upload(UUID policyId, MultipartFile file) {
        var policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId));

        var docId = UUID.randomUUID();
        var s3Key = "policies/%s/%s".formatted(policyId, docId);

        try {
            var putRequest = PutObjectRequest.builder()
                    .bucket(s3Bucket)
                    .key(s3Key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(file.getBytes()));
        } catch (Exception e) {
            throw new DocumentUploadException(e.getMessage(), e);
        }

        var document = new Document(docId, policy, file.getOriginalFilename(),
                s3Key, file.getContentType(), file.getSize(), Instant.now());
        documentRepository.save(document);

        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(UUID policyId) {
        return documentRepository.findByPolicyIdOrderByUploadedAtDesc(policyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        var doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Bucket)
                .key(doc.getS3Key())
                .build());

        documentRepository.delete(doc);
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(doc.getId(), doc.getPolicy().getId(),
                doc.getFileName(), doc.getContentType(), doc.getFileSize(), doc.getUploadedAt());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest="DocumentServiceTest" -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/policy/service/DocumentService.java \
  src/test/java/com/insurance/policy/service/DocumentServiceTest.java
git commit -m "feat: add DocumentService with S3 upload"
```

---

### Task 6: DocumentController

**Files:**
- Create: `src/main/java/com/insurance/policy/web/DocumentController.java`
- Create: `src/test/java/com/insurance/policy/web/DocumentControllerTest.java`

- [ ] **Step 1: Write the controller test**

Create `src/test/java/com/insurance/policy/web/DocumentControllerTest.java`:

```java
package com.insurance.policy.web;

import com.insurance.policy.config.SecurityConfig;
import com.insurance.policy.config.JwtAuthenticationFilter;
import com.insurance.policy.dtos.DocumentResponse;
import com.insurance.policy.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DocumentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}))
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void shouldUploadDocument() throws Exception {
        var policyId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "test content".getBytes());

        var response = new DocumentResponse(UUID.randomUUID(), policyId,
                "policy.pdf", "application/pdf", 12L, Instant.now());

        when(documentService.upload(eq(policyId), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policyId)
                        .file(file)
                        .contentType("multipart/form-data"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("policy.pdf"));
    }

    @Test
    void shouldListDocuments() throws Exception {
        var policyId = UUID.randomUUID();

        when(documentService.getDocuments(policyId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/policies/{policyId}/documents", policyId))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400WhenFileIsMissing() throws Exception {
        var policyId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policyId))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest="DocumentControllerTest" -DfailIfNoTests=false`
Expected: Compilation error — `DocumentController` not found.

- [ ] **Step 3: Create DocumentController**

Create `src/main/java/com/insurance/policy/web/DocumentController.java`:

```java
package com.insurance.policy.web;

import com.insurance.policy.dtos.DocumentResponse;
import com.insurance.policy.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(path = "/policies/{policyId}/documents",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<DocumentResponse> uploadDocument(
            @PathVariable UUID policyId,
            @RequestParam("file") MultipartFile file) {
        var response = documentService.upload(policyId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/policies/{policyId}/documents")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<DocumentResponse>> getDocuments(
            @PathVariable UUID policyId) {
        return ResponseEntity.ok(documentService.getDocuments(policyId));
    }

    @DeleteMapping("/documents/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest="DocumentControllerTest" -DfailIfNoTests=false`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/insurance/policy/web/DocumentController.java \
  src/test/java/com/insurance/policy/web/DocumentControllerTest.java
git commit -m "feat: add DocumentController with multipart upload"
```

---

### Task 7: Integration Tests — Testcontainers LocalStack + BaseContainerTest

**Files:**
- Modify: `src/test/java/com/insurance/policy/BaseContainerTest.java`
- Modify: `src/test/resources/application.yaml`
- Create: `src/test/java/com/insurance/policy/DocumentIntegrationTest.java`

- [ ] **Step 1: Add LocalStack to BaseContainerTest**

Replace `BaseContainerTest.java` contents with:

```java
package com.insurance.policy;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

@Testcontainers
public abstract class BaseContainerTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbit =
            new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:alpine").withExposedPorts(6379);

    @Container
    static LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0.2"))
                    .withServices(S3);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        registry.add("aws.s3.endpoint", localstack::getEndpoint);
        registry.add("aws.s3.region", localstack::getRegion);
        registry.add("aws.s3.bucket", () -> "insurance-documents");
    }
}
```

- [ ] **Step 2: Add S3 config to test application.yaml**

Add to `src/test/resources/application.yaml`:

```yaml
spring:
  # ... existing ...
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

- [ ] **Step 3: Write the integration test**

Create `src/test/java/com/insurance/policy/DocumentIntegrationTest.java`:

```java
package com.insurance.policy;

import com.insurance.policy.domain.Policy;
import com.insurance.policy.domain.PolicyStatus;
import com.insurance.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DocumentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    void shouldUploadDocumentToPolicy() throws Exception {
        var policy = new Policy();
        policy.setId(UUID.randomUUID());
        policy.setPolicyNumber("DOC-TEST-001");
        policy.setPolicyHolder("Test Holder");
        policy.setCoverageAmount(new BigDecimal("100000"));
        policy.setPremiumAmount(new BigDecimal("500"));
        policy.setStartDate(LocalDate.now());
        policy.setStatus(PolicyStatus.DRAFT);
        policyRepository.save(policy);

        var file = new MockMultipartFile("file", "report.pdf",
                "application/pdf", "pdf content".getBytes());

        var token = obtainToken("user");
        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policy.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyId").value(policy.getId().toString()))
                .andExpect(jsonPath("$.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").value(12));
    }

    @Test
    void shouldReturnDocumentsForPolicy() throws Exception {
        var policy = new Policy();
        policy.setId(UUID.randomUUID());
        policy.setPolicyNumber("DOC-TEST-002");
        policy.setPolicyHolder("Test Holder");
        policy.setCoverageAmount(new BigDecimal("100000"));
        policy.setPremiumAmount(new BigDecimal("500"));
        policy.setStartDate(LocalDate.now());
        policy.setStatus(PolicyStatus.DRAFT);
        policyRepository.save(policy);

        var file = new MockMultipartFile("file", "doc.pdf",
                "application/pdf", "data".getBytes());

        var token = obtainToken("user");
        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policy.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/policies/{policyId}/documents", policy.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `./mvnw verify -DskipPitest=true`
Expected: All tests pass (unit + web slice + integration)

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/insurance/policy/BaseContainerTest.java \
  src/test/resources/application.yaml \
  src/test/java/com/insurance/policy/DocumentIntegrationTest.java
git commit -m "test: add DocumentIntegrationTest with Testcontainers LocalStack"
```

---

### Self-Review Checklist

1. **Spec coverage:** All spec sections covered — infrastructure (Task 1), migration (Task 2), domain (Task 3), DTOs/exceptions (Task 4), service (Task 5), controller (Task 6), integration tests (Task 7).
2. **Placeholder scan:** No TODOs, TBDs, or incomplete code blocks.
3. **Type consistency:** `DocumentService.upload` returns `DocumentResponse`, `DocumentController` returns `ResponseEntity<DocumentResponse>`, S3 key format `policies/{policyId}/{uuid}` consistent throughout.
