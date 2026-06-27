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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.net.URL;

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

    @Mock
    private S3Presigner s3Presigner;

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
        var file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

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
        var file = new MockMultipartFile("file", "test.pdf", "application/pdf", "data".getBytes());

        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 unavailable"));

        assertThrows(DocumentUploadException.class, () -> documentService.upload(policyId, file));
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(documentRepository, never()).save(any());
    }

    @Test
    void shouldUploadToS3WithCorrectKeyPrefix() throws Exception {
        var policyId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(policyId);
        var file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "data".getBytes());

        var docCaptor = ArgumentCaptor.forClass(Document.class);
        when(policyRepository.findById(policyId)).thenReturn(Optional.of(policy));
        when(documentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        documentService.upload(policyId, file);

        verify(documentRepository).save(docCaptor.capture());
        assertTrue(docCaptor.getValue().getS3Key().startsWith("policies/" + policyId + "/"));
    }

    @Test
    void shouldListDocumentsByPolicyId() {
        var policyId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(policyId);
        var doc = Document.builder()
                .id(UUID.randomUUID())
                .policy(policy)
                .fileName("a.pdf")
                .s3Key("key")
                .contentType("pdf")
                .fileSize(100L)
                .uploadedAt(Instant.now())
                .build();

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
        var doc = Document.builder()
                .id(docId)
                .policy(policy)
                .fileName("a.pdf")
                .s3Key("s3/key")
                .contentType("pdf")
                .fileSize(100L)
                .uploadedAt(Instant.now())
                .build();

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

    @Test
    void shouldReturnPresignedUrlForDownload() throws Exception {
        var docId = UUID.randomUUID();
        var policy = new Policy();
        policy.setId(UUID.randomUUID());
        var doc = Document.builder()
                .id(docId)
                .policy(policy)
                .fileName("a.pdf")
                .s3Key("policies/key")
                .contentType("pdf")
                .fileSize(100L)
                .uploadedAt(Instant.now())
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        var presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URI("https://s3.example.com/file").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presigned);

        var url = documentService.getDownloadUrl(docId);

        assertEquals("https://s3.example.com/file", url);
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void shouldThrowDocumentNotFoundExceptionWhenDownloadingNonExistent() {
        var docId = UUID.randomUUID();
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class,
                () -> documentService.getDownloadUrl(docId));
        verify(s3Presigner, never()).presignGetObject(any(GetObjectPresignRequest.class));
    }
}
