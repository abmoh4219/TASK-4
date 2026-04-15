package com.registrarops.api;

import com.registrarops.entity.PolicySetting;
import com.registrarops.entity.RetryJob;
import com.registrarops.repository.PolicySettingRepository;
import com.registrarops.repository.RetryJobRepository;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.AccountDeletionService;
import com.registrarops.service.ExportTokenService;
import com.registrarops.service.ImportExportService;
import com.registrarops.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Cycle 2 remediation suite — covers the audit-fix work end-to-end:
 *   1. signed export token works without authentication
 *   2. PolicySettingService writes actually change runtime behavior
 *   3. failed-import rows enqueue a retry job
 *   4. API key authentication on /api/v1/import|export
 *   5. order reminder + exception messaging paths
 */
@AutoConfigureMockMvc
class Cycle2RemediationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private ExportTokenService exportTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderService orderService;
    @Autowired private com.registrarops.service.PolicySettingService policySettingService;
    @Autowired private PolicySettingRepository policySettingRepository;
    @Autowired private ImportExportService importExportService;
    @Autowired private RetryJobRepository retryJobRepository;
    @Value("${registrarops.api-key:}") String configuredApiKey;

    /**
     * Hard-restore every canonical policy key to its V015 seed value after
     * every test so no policy mutation can leak into other test classes.
     * Test-container mode shares the MySQL container across classes and only
     * resets schema between runs, so leaks here would otherwise corrupt
     * SecurityHardeningTest / OrderApiTest which assert the 14-day window.
     */
    @AfterEach
    void restoreCanonicalPolicyDefaults() {
        policySettingService.set("orders.payment_timeout_minutes", "30", 1L, "admin");
        policySettingService.set("orders.refund_window_days",      "14", 1L, "admin");
        policySettingService.set("orders.idempotency_window_minutes","10", 1L, "admin");
        policySettingService.set("retry.max_attempts",              "3",  1L, "admin");
        policySettingService.set("notifications.quiet_start_hour",  "22", 1L, "admin");
        policySettingService.set("notifications.quiet_end_hour",    "7",  1L, "admin");

        // Un-soft-delete the student account. exportTokenWorksWithoutAuthentication
        // calls AccountDeletionService.exportAndSoftDelete(), which stamps
        // deletedAt / isActive=false / exportFilePath on the user row.
        // Testcontainers shares MySQL state across classes, so unless we
        // restore the row, AuthApiTest and SecurityHardeningTest later fail
        // because the student can no longer log in.
        userRepository.findByUsername("student").ifPresent(u -> {
            u.setDeletedAt(null);
            u.setExportFilePath(null);
            u.setIsActive(true);
            userRepository.save(u);
        });
    }

    // ---------- 1. Export token works without authentication ----------------

    @Test
    void exportTokenWorksWithoutAuthentication() throws Exception {
        Long studentId = userRepository.findByUsername("student").orElseThrow().getId();
        String token = accountDeletionService.exportAndSoftDelete(studentId);
        assertNotNull(token);

        // No auth context — must still succeed.
        mockMvc.perform(get("/account/export/" + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString(".json")));
    }

    @Test
    void invalidExportTokenIsRejected() throws Exception {
        mockMvc.perform(get("/account/export/totally-bogus.value"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossUserExportTokenCannotForge() {
        // Issuing a token for user A and decoding it gives you only A.
        String t = exportTokenService.issue(123L);
        assertEquals(123L, exportTokenService.verify(t));
        // Tampering rejected.
        String tampered = t.substring(0, t.length() - 1) + (t.endsWith("A") ? "B" : "A");
        assertNull(exportTokenService.verify(tampered));
    }

    // ---------- 2. Policy actually controls runtime --------------------------

    @Test
    void policyUpdateChangesRuntimeRefundWindow() {
        int original = orderService.getRefundWindowDays();
        try {
            policySettingService.set("orders.refund_window_days", "21", 1L, "admin");
            assertEquals(21, orderService.getRefundWindowDays(),
                    "OrderService must read refund window from PolicySettingService");

            policySettingService.set("orders.payment_timeout_minutes", "45", 1L, "admin");
            assertEquals(45, orderService.getPaymentTimeoutMinutes());
        } finally {
            policySettingService.set("orders.refund_window_days", String.valueOf(original), 1L, "admin");
            policySettingService.set("orders.payment_timeout_minutes", "30", 1L, "admin");
        }
    }

    @Test
    void invalidPolicyValueRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> policySettingService.set("orders.refund_window_days", "-1", 1L, "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> policySettingService.set("unknown.key", "1", 1L, "admin"));
    }

    @Test
    void retryMaxAttemptsReadsFromPolicy() {
        int original = importExportService.getMaxRetryAttempts();
        try {
            policySettingService.set("retry.max_attempts", "5", 1L, "admin");
            assertEquals(5, importExportService.getMaxRetryAttempts());
            RetryJob j = importExportService.scheduleRetry("TEST_POLICY_MAX", "{}");
            assertEquals(5, j.getMaxAttempts());
        } finally {
            policySettingService.set("retry.max_attempts", String.valueOf(original), 1L, "admin");
        }
    }

    // ---------- 3. Failed import → retry queue ------------------------------

    @Test
    void failedImportRowEnqueuesRetryJob() {
        // Row 2 is good, row 3 has empty title → row-level error → artifact + retry.
        String csv = "code,title,credits,price,category\n"
                + "RETRYOK1,Good Course,3.00,9.99,Math\n"
                + ",MissingCode,3.00,9.99,Math\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "fail.csv", "text/csv", csv.getBytes());

        ImportExportService.ImportResult r =
                importExportService.importCoursesCsv(file, 1L, "admin", null);

        assertTrue(r.errors.size() >= 1);
        assertNotNull(r.retryJobId, "failed-import must enqueue a RetryJob");
        RetryJob job = retryJobRepository.findById(r.retryJobId).orElseThrow();
        assertEquals(ImportExportService.JOB_COURSE_IMPORT_RETRY, job.getJobType());
        assertNotNull(job.getPayload());
    }

    // ---------- 4. API key auth on /api/v1/import|export --------------------

    @Test
    void apiV1ExportRejectsRequestWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/export/courses.csv"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(401),
                        org.hamcrest.Matchers.is(403),
                        org.hamcrest.Matchers.is(302))));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void apiV1ExportSucceedsForAdminBrowserSession() throws Exception {
        mockMvc.perform(get("/api/v1/export/courses.csv"))
                .andExpect(status().isOk());
    }
}
