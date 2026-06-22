package com.insurance.policy.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DocumentTest {

    @Test
    void shouldCreateDocument() {
        var policy = Policy.builder().id(UUID.randomUUID()).build();
        var id = UUID.randomUUID();
        var now = Instant.now();

        var doc = Document.builder()
                .id(id)
                .policy(policy)
                .fileName("policy.pdf")
                .s3Key("policies/" + policy.getId() + "/" + id)
                .contentType("application/pdf")
                .fileSize(1024L)
                .uploadedAt(now)
                .build();

        assertEquals(id, doc.getId());
        assertEquals(policy.getId(), doc.getPolicy().getId());
        assertEquals("policy.pdf", doc.getFileName());
        assertEquals("application/pdf", doc.getContentType());
        assertEquals(1024L, doc.getFileSize());
        assertEquals(now, doc.getUploadedAt());
    }
}
