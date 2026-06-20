package com.insurance.policy.exception;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DocumentNotFoundExceptionTest {

    @Test
    void shouldHaveCorrectMessage() {
        var id = UUID.randomUUID();
        var ex = new DocumentNotFoundException(id);
        assertEquals("Document not found with id: " + id, ex.getMessage());
    }
}
