package com.insurance.policy.service;

import com.insurance.policy.domain.Document;
import com.insurance.policy.dtos.DocumentResponse;
import com.insurance.policy.exception.*;
import com.insurance.policy.repository.DocumentRepository;
import com.insurance.policy.repository.PolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private final PolicyRepository policyRepository;
    private final DocumentRepository documentRepository;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String s3Bucket;
    private static final Set<String> ALLOWED = Set.of("application/pdf","image/jpeg","image/png");
    private static final long MAX_BYTES = 10 * 1024 * 1024L;

    public DocumentService(PolicyRepository policyRepository,
                           DocumentRepository documentRepository,
                           S3Client s3Client,
                           S3Presigner s3Presigner,
                           String s3Bucket) {
        this.policyRepository = policyRepository;
        this.documentRepository = documentRepository;
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.s3Bucket = s3Bucket;
    }

    @Transactional
    public DocumentResponse upload(UUID policyId, MultipartFile file) {
        if (!ALLOWED.contains(file.getContentType()))
            throw new UnsupportedDocumentTypeException(file.getContentType());
        if (file.getSize() > MAX_BYTES)
            throw new DocumentTooLargeException(file.getSize());
        var policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyNotFoundException(policyId.toString()));

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

        var document = Document.builder()
                .id(docId)
                .policy(policy)
                .fileName(file.getOriginalFilename())
                .s3Key(s3Key)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .uploadedAt(Instant.now())
                .build();

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

    public String getDownloadUrl(UUID documentId) {
        var doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        var getRequest = GetObjectRequest.builder()
                .bucket(s3Bucket)
                .key(doc.getS3Key())
                .build();

        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .getObjectRequest(getRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toExternalForm();
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(doc.getId(), doc.getPolicy().getId(),
                doc.getFileName(), doc.getContentType(), doc.getFileSize(), doc.getUploadedAt());
    }
}
