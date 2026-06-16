package com.insurance.policy.exception;

import com.insurance.policy.domain.Policy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.ClassTypeInformation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleNotFound() {
        PolicyNotFoundException ex = new PolicyNotFoundException("POL-001");

        ResponseEntity<ApiError> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("POL-001"));
        assertEquals(404, response.getBody().getStatus());
    }

    @Test
    void shouldHandleAlreadyExists() {
        PolicyAlreadyExistsException ex = new PolicyAlreadyExistsException("POL-001");

        ResponseEntity<ApiError> response = handler.handleAlreadyExists(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().getStatus());
    }

    @Test
    void shouldHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument");

        ResponseEntity<ApiError> response = handler.handleIllegalArgument(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("Invalid argument"));
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void shouldHandleInvalidTransition() {
        InvalidPolicyTransitionException ex =
                new InvalidPolicyTransitionException(Policy.PolicyStatus.DRAFT, Policy.PolicyStatus.CANCELLED);

        ResponseEntity<ApiError> response = handler.handleInvalidTransition(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void shouldHandlePropertyReference() {
        PropertyReferenceException ex = new PropertyReferenceException(
                "invalidProp",
                ClassTypeInformation.from(Policy.class),
                java.util.List.of());

        ResponseEntity<ApiError> response = handler.handlePropertyReference(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains("invalidProp"));
        assertEquals(400, response.getBody().getStatus());
    }

    @Test
    void shouldHandleAllExceptions() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<ApiError> response = handler.handleAllExceptions(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getStatus());
    }

    @Test
    void shouldHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<ApiError> response = handler.handleAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getStatus());
    }
}
