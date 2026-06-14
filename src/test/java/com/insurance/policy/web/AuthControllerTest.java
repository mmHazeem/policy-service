package com.insurance.policy.web;

import com.insurance.policy.config.AuditConfig;
import com.insurance.policy.config.AuthService;
import com.insurance.policy.config.JwtAuthenticationFilter;
import com.insurance.policy.config.SecurityConfig;
import com.insurance.policy.dtos.UserResponse;
import com.insurance.policy.domain.User;
import com.insurance.policy.policy_service.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, AuditConfig.class}
        )
)
@Import(AuthControllerTest.TestSecurityConfig.class)
class AuthControllerTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(s ->
                            s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
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

    @MockitoBean private AuthService authService;
    @MockitoBean private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturn200WhenAdminListsUsers() throws Exception {
        when(userDetailsService.getAllUsers()).thenReturn(List.of(
                new UserResponse(UUID.randomUUID(), "admin", User.Role.ADMIN)
        ));

        mockMvc.perform(get("/api/v1/auth/getall"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldReturn403WhenUserListsUsers() throws Exception {
        mockMvc.perform(get("/api/v1/auth/getall"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn401WhenListingUsersWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/auth/getall"))
                .andExpect(status().isUnauthorized());
    }
}
