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
