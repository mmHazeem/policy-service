package com.insurance.policy.web;

import com.insurance.policy.config.AuditConfig;
import com.insurance.policy.config.CorrelationIdFilter;
import com.insurance.policy.config.JwtAuthenticationFilter;
import com.insurance.policy.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        value = DummyController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, AuditConfig.class}
        )
)
@Import(CorrelationIdFilterTest.TestSecurityConfig.class)
class CorrelationIdFilterTest {

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        public CorrelationIdFilter correlationIdFilter() {
            return new CorrelationIdFilter();
        }

        @Bean
        public SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .sessionManagement(s ->
                            s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            .anyRequest().permitAll()
                    )
                    .addFilterBefore(correlationIdFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() throws Exception {
        mockMvc.perform(get("/_test"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void shouldPropagateCorrelationIdFromRequestHeader() throws Exception {
        mockMvc.perform(get("/_test").header("X-Correlation-Id", "test-id-123"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-id-123"));
    }
}
