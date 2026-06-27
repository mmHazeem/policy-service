package com.insurance.policy.web;

import com.insurance.policy.config.AuditConfig;
import com.insurance.policy.config.JwtAuthenticationFilter;
import com.insurance.policy.config.SecurityConfig;
import com.insurance.policy.dtos.DocumentResponse;
import com.insurance.policy.exception.DocumentNotFoundException;
import com.insurance.policy.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = DocumentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, JwtAuthenticationFilter.class, AuditConfig.class}
        ))
@Import(DocumentControllerTest.TestSecurityConfig.class)
class DocumentControllerTest {

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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldUploadDocument() throws Exception {
        var policyId = UUID.randomUUID();
        var file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "test content".getBytes());

        var response = new DocumentResponse(UUID.randomUUID(), policyId,
                "policy.pdf", "application/pdf", 12L, Instant.now());

        when(documentService.upload(eq(policyId), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policyId)
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("policy.pdf"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldListDocuments() throws Exception {
        var policyId = UUID.randomUUID();

        when(documentService.getDocuments(policyId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/policies/{policyId}/documents", policyId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldReturn400WhenFileIsMissing() throws Exception {
        var policyId = UUID.randomUUID();

        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policyId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldReturnPresignedUrlForDownload() throws Exception {
        var docId = UUID.randomUUID();
        when(documentService.getDownloadUrl(docId))
                .thenReturn("https://s3.example.com/doc.pdf?X-Amz-Signature=abc");

        mockMvc.perform(get("/api/v1/documents/{documentId}/download", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://s3.example.com/doc.pdf?X-Amz-Signature=abc"));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void shouldReturn404WhenDocumentNotFoundForDownload() throws Exception {
        var docId = UUID.randomUUID();
        when(documentService.getDownloadUrl(docId))
                .thenThrow(new DocumentNotFoundException(docId));

        mockMvc.perform(get("/api/v1/documents/{documentId}/download", docId))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn401WhenNotAuthenticatedForDownload() throws Exception {
        mockMvc.perform(get("/api/v1/documents/{documentId}/download", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
