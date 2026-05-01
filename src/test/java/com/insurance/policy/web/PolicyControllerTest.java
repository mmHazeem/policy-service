package com.insurance.policy.web;

import com.insurance.policy.config.SecurityConfig;
import com.insurance.policy.dtos.PolicyRequest;
import com.insurance.policy.dtos.PolicyResponse;
import com.insurance.policy.policy_service.PolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@Import(SecurityConfig.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyService policyService;

    @Test
    @WithMockUser(username = "admin")
    void shouldCreatePolicySuccessfully() throws Exception {
        // Arrange
        PolicyResponse response = new PolicyResponse(
                UUID.randomUUID(), "POL-123", "John Doe",
                new BigDecimal("5000"), new BigDecimal("25"),
                LocalDate.now(), "CREATED");

        when(policyService.createPolicy(any(PolicyRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf()) // Spring Security usually needs this in tests
                        .contentType(String.valueOf(MediaType.APPLICATION_JSON))
                        .content("""
                    {
                        "policyNumber": "POL-123",
                        "policyHolder": "John Doe",
                        "coverageAmount": 5000,
                        "startDate": "2026-04-20"
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyNumber").value("POL-123"))
                .andExpect(jsonPath("$.premiumAmount").value(25.0));
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldGetPolicySuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isOk());
    }
}