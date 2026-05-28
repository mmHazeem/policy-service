package com.insurance.policy.web;

import com.insurance.policy.config.JwtAuthenticationFilter;
import com.insurance.policy.config.SecurityConfig;
import com.insurance.policy.policy_service.PolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyController.class)
@Import(SecurityConfig.class)
class PolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyService policyService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn202WhenPolicyRequestIsValid() throws Exception {
        // Controller sends to RabbitMQ and returns 202 Accepted
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "policyNumber": "POL-123",
                                    "policyHolder": "John Doe",
                                    "coverageAmount": 5000,
                                    "startDate": "2026-04-20"
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenCoverageAmountIsBelowMinimum() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "policyNumber": "POL-123",
                                    "policyHolder": "John Doe",
                                    "coverageAmount": 500,
                                    "startDate": "2026-04-20"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn200WhenListingPolicies() throws Exception {
        mockMvc.perform(get("/api/v1/policies"))
                .andExpect(status().isOk());
    }
}