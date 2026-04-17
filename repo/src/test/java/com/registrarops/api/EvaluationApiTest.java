package com.registrarops.api;

import com.registrarops.entity.EvaluationCycle;
import com.registrarops.entity.EvaluationStatus;
import com.registrarops.repository.EvaluationCycleRepository;
import com.registrarops.service.EvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EvaluationApiTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EvaluationService evaluationService;
    @Autowired private EvaluationCycleRepository cycleRepository;

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testUploadEvidence() throws Exception {
        EvaluationCycle c = evaluationService.createCycle(1L, 2L, "Mid-term audit");
        MockMultipartFile pdf = new MockMultipartFile("file", "report.pdf",
                "application/pdf", "%PDF-1.4 fake content".getBytes());
        mockMvc.perform(multipart("/evaluations/{id}/evidence", c.getId())
                        .file(pdf).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/evaluations/*"));

        var evidence = evaluationService.evidenceFor(c.getId());
        assertFalse(evidence.isEmpty(), "evidence attachment must be persisted");
        assertEquals("report.pdf", evidence.get(0).getOriginalFilename(),
                "stored filename must match uploaded filename");
        assertEquals("application/pdf", evidence.get(0).getMimeType(),
                "stored MIME type must match uploaded MIME type");
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testEvidenceExceeds10MBRejected() throws Exception {
        EvaluationCycle c = evaluationService.createCycle(1L, 2L, "Big upload audit");
        byte[] big = new byte[(int) (EvaluationService.MAX_EVIDENCE_BYTES + 1)];
        MockMultipartFile huge = new MockMultipartFile("file", "huge.pdf", "application/pdf", big);
        mockMvc.perform(multipart("/evaluations/{id}/evidence", c.getId())
                        .file(huge).with(csrf()))
                .andExpect(status().is3xxRedirection());
        // Service throws → controller catches → flashError, no row inserted
        assertTrue(evaluationService.evidenceFor(c.getId()).isEmpty());
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testInvalidMimeTypeRejected() throws Exception {
        EvaluationCycle c = evaluationService.createCycle(1L, 2L, "Bad mime");
        MockMultipartFile exe = new MockMultipartFile("file", "evil.exe",
                "application/x-msdownload", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/evaluations/{id}/evidence", c.getId())
                        .file(exe).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(evaluationService.evidenceFor(c.getId()).isEmpty());
    }

    @Test
    void testOutlierDetectionFlags() {
        EvaluationCycle c = evaluationService.createCycle(1L, 2L, "Outlier audit");
        // Six tightly-clustered indicators around 80 and one extreme low score.
        // mean ≈ 68.6, σ ≈ 28.0, 2σ ≈ 56.0 — score 0 is at distance ~68.6 → outlier.
        evaluationService.addIndicator(c.getId(), "indA", new BigDecimal("100"), new BigDecimal("80"));
        evaluationService.addIndicator(c.getId(), "indB", new BigDecimal("100"), new BigDecimal("82"));
        evaluationService.addIndicator(c.getId(), "indC", new BigDecimal("100"), new BigDecimal("78"));
        evaluationService.addIndicator(c.getId(), "indD", new BigDecimal("100"), new BigDecimal("81"));
        evaluationService.addIndicator(c.getId(), "indE", new BigDecimal("100"), new BigDecimal("80"));
        evaluationService.addIndicator(c.getId(), "indF", new BigDecimal("100"), new BigDecimal("79"));
        evaluationService.addIndicator(c.getId(), "indG", new BigDecimal("100"), new BigDecimal("0"));

        var outliers = evaluationService.detectOutliers(c.getId());
        assertEquals(1, outliers.size());
        assertEquals("indG", outliers.get(0).getIndicatorName());
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    void testReviewerApprovesCycle() {
        EvaluationCycle c = evaluationService.createCycle(1L, 2L, "Approve me");
        evaluationService.addIndicator(c.getId(), "indA", new BigDecimal("100"), new BigDecimal("80"));
        evaluationService.openCycle(c.getId(), 2L);
        evaluationService.submitCycle(c.getId(), 2L);
        evaluationService.reviewerApprove(c.getId(), 3L, "Looks good");
        assertEquals(EvaluationStatus.CLOSED, cycleRepository.findById(c.getId()).orElseThrow().getStatus());
    }
}
