package com.insurance.policy.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentUploadExceptionTest {

    @Test
    void shouldHaveCorrectMessageAndCause() {
        var cause = new RuntimeException("S3 error");
        var ex = new DocumentUploadException("connection timeout", cause);
        assertEquals("Failed to upload document: connection timeout", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
