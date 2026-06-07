package com.insurance.policy.web;

import com.insurance.policy.config.JwtAuthenticationFilter;
import com.insurance.policy.config.SecurityConfig;
import com.insurance.policy.policy_service.PolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = PolicyController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                // Exclude the real SecurityConfig (would add JWT filter to chain)
                // Exclude JwtAuthenticationFilter (Filter bean picked up by @WebMvcTest)
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class}
        )
)
@Import(PolicyControllerTest.TestSecurityConfig.class)
class PolicyControllerTest {
    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(s ->
                            s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/auth/**").permitAll()
                            .anyRequest().authenticated()
                    )
                    .exceptionHandling(ex -> ex
                            .authenticationEntryPoint((request, response, authException) ->
                                    response.sendError(401, "Unauthorized"))
                    )
                    .build();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private PolicyService policyService;

    // Valid request body
    private static final String VALID_BODY = """
            {
                "policyNumber": "POL-123",
                "policyHolder": "John Doe",
                "coverageAmount": 5000,
                "startDate": "2026-04-20"
            }
            """;
    @Test
    @WithMockUser(username = "admin")
    void shouldReturn202WhenPolicyRequestIsValid() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted()); // 202 — controller publishes to RabbitMQ
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenPolicyNumberIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "policyNumber": "",
                                    "policyHolder": "John Doe",
                                    "coverageAmount": 5000,
                                    "startDate": "2026-04-20"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.policyNumber").exists());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenPolicyHolderIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "policyNumber": "POL-123",
                                    "policyHolder": "",
                                    "coverageAmount": 5000,
                                    "startDate": "2026-04-20"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.policyHolder").exists());
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.coverageAmount").exists());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenCoverageAmountIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "policyNumber": "POL-123",
                                    "policyHolder": "John Doe",
                                    "startDate": "2026-04-20"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.coverageAmount").exists());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenRequestBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn200WhenPatchStatusIsValid() throws Exception {
        mockMvc.perform(patch("/api/v1/policies/{id}/status", "550e8400-e29b-41d4-a716-446655440000")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ACTIVE"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin")
    void shouldReturn400WhenPatchStatusIsNull() throws Exception {
        mockMvc.perform(patch("/api/v1/policies/{id}/status", "550e8400-e29b-41d4-a716-446655440000")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": null}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401WhenGettingPoliciesWithoutToken() throws Exception {
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