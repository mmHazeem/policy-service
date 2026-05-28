package com.insurance.policy.policy_service;

import com.insurance.policy.dtos.AuthRequest;
import com.insurance.policy.dtos.AuthResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.junit.jupiter.Testcontainers;

// Shared Testcontainers base for all integration tests.
@Testcontainers
public abstract class BaseIntegrationTest extends BaseContainerTest{
    @Autowired
    private TestRestTemplate restTemplate;

    // Registers a fresh user and returns the JWT token.
    protected String obtainToken(String username) {
        AuthRequest request = new AuthRequest(username, "password123");
        ResponseEntity<AuthResponse> response = restTemplate
                .postForEntity("/api/v1/auth/register", request, AuthResponse.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new IllegalStateException(
                    "Failed to register test user '" + username +
                            "'. HTTP " + response.getStatusCode());
        }
        return response.getBody().token();
    }

    // Convenience: JSON + Bearer token headers ready for exchange().
    protected HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    // Convenience: plain JSON headers (no auth) for testing 401 paths.
    protected HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

}
