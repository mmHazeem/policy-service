package com.insurance.policy;

import com.insurance.policy.domain.Policy;
import com.insurance.policy.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class DocumentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PolicyRepository policyRepository;

    @Test
    void shouldUploadDocumentToPolicy() throws Exception {
        var policy = policyRepository.save(Policy.builder()
                .policyNumber("DOC-INT-001")
                .policyHolder("Test Holder")
                .coverageAmount(new BigDecimal("100000"))
                .premiumAmount(new BigDecimal("500"))
                .startDate(LocalDate.now())
                .status(Policy.PolicyStatus.DRAFT)
                .build());

        var file = new MockMultipartFile("file", "report.pdf",
                "application/pdf", "pdf content".getBytes());

        var token = obtainToken("user");
        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policy.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyId").value(policy.getId().toString()))
                .andExpect(jsonPath("$.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileSize").value(12));
    }

    @Test
    void shouldReturnDocumentsForPolicy() throws Exception {
        var policy = policyRepository.save(Policy.builder()
                .policyNumber("DOC-INT-002")
                .policyHolder("Test Holder")
                .coverageAmount(new BigDecimal("100000"))
                .premiumAmount(new BigDecimal("500"))
                .startDate(LocalDate.now())
                .status(Policy.PolicyStatus.DRAFT)
                .build());

        var file = new MockMultipartFile("file", "doc.pdf",
                "application/pdf", "data".getBytes());

        var token = obtainToken("user2");
        mockMvc.perform(multipart("/api/v1/policies/{policyId}/documents", policy.getId())
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/policies/{policyId}/documents", policy.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
