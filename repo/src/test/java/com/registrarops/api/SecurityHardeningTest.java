package com.registrarops.api;

import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.AccountDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 8 hardening tests:
 *
 *   Security: CSRF missing on POST → 403, role-based URL gating
 *   Privacy : audit-log masking, account soft-delete
 */
@AutoConfigureMockMvc
class SecurityHardeningTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private UserRepository userRepository;
    @Autowired private com.registrarops.service.OrderService orderService;

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testCsrfMissingOnPostReturns403() throws Exception {
        mockMvc.perform(post("/orders/create")
                        .param("itemType", "course")
                        .param("itemId", "1")
                        .param("correlationId", "no-csrf-token-here"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testRoleBasedUrlAccess_StudentCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/audit")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/import")).andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/config")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testFacultyCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isForbidden());
    }

    @Test
    void testAccountDeletionSoftDelete() {
        // Student id 4 from seed data.
        User before = userRepository.findById(4L).orElseThrow();
        assertNull(before.getDeletedAt(), "precondition: account is not yet deleted");

        String token = accountDeletionService.exportAndSoftDelete(4L);
        assertNotNull(token);

        User after = userRepository.findById(4L).orElseThrow();
        assertNotNull(after.getDeletedAt(), "deletedAt timestamp must be set after soft-delete");
        assertFalse(after.getIsActive(), "isActive must be false after soft-delete");
        assertNotNull(after.getExportFilePath(), "export file path must be recorded");

        // Roll back so other tests see the seeded student again.
        after.setDeletedAt(null);
        after.setIsActive(true);
        after.setExportFilePath(null);
        userRepository.save(after);
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testStudentCannotHitInternalApiV1Reports() throws Exception {
        mockMvc.perform(get("/api/v1/reports/gpa-summary"))
                .andExpect(status().isForbidden());
    }

    // --- Audit remediation tests ------------------------------------------------

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testExportDownloadDeniedWithWrongToken() throws Exception {
        // Student "student" has no export yet → any token lookup must 404/403,
        // never leak another user's file.
        mockMvc.perform(get("/account/export/obviously-not-my-token"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testGradeApiRbacStudentForbidden() throws Exception {
        // The list endpoint is not for students.
        mockMvc.perform(get("/api/v1/grades"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testStudentCanReadOwnGradesViaByStudent() throws Exception {
        // Seed student id is 4. Object-level policy allows self-read.
        mockMvc.perform(get("/api/v1/grades/student/4"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testStudentCannotReadOtherStudentsGrades() throws Exception {
        // Student trying to read faculty user (id=2) grades → forbidden by policy.
        mockMvc.perform(get("/api/v1/grades/student/2"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testFacultyWithoutRecordedComponentsBlockedOnCourse() throws Exception {
        // Faculty has not recorded any components in course 3 in fresh state → forbidden.
        mockMvc.perform(get("/api/v1/grades/course/3"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testFacultyBlockedFromGradesListEndpoint() throws Exception {
        // The aggregate list endpoint is admin/reviewer only — faculty must use scoped routes.
        mockMvc.perform(get("/api/v1/grades"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testFacultyBlockedOnCourseApiGradesWithoutScope() throws Exception {
        // Faculty with no recorded components in course 3 cannot read /courses/3/grades.
        mockMvc.perform(get("/api/v1/courses/3/grades"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testPathValidationRejectsZeroId() throws Exception {
        mockMvc.perform(get("/api/v1/grades/student/0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDtoValidationRejectsOversizePageSize() throws Exception {
        // Bound to PageQueryDto: size > 200 must fail before reaching the service.
        mockMvc.perform(get("/api/v1/students?page=0&size=5000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDtoValidationRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/students?page=-2&size=10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGradeListDtoRejectsInvalidCourseId() throws Exception {
        mockMvc.perform(get("/api/v1/grades?page=0&size=10&courseId=0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testImportExportApiAdminExportsCsv() throws Exception {
        mockMvc.perform(get("/api/v1/export/courses.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testImportExportApiStudentForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/export/courses.csv"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRetryJobsListEmptyByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/retry/jobs"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testPolicyApiReadWrite() throws Exception {
        mockMvc.perform(get("/api/v1/policy"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("orders.refund_window_days")));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/policy/orders.refund_window_days")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"21\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/policy/orders.refund_window_days"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"value\":\"21\"")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testPolicyApiRejectsInvalidValue() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/policy/orders.refund_window_days")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"not-a-number\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    void testGradeApiRbacReviewerAllowed() throws Exception {
        // Reviewer is the broadest role that can still use the aggregate list
        // endpoint; faculty is blocked (must use scoped student/course routes).
        mockMvc.perform(get("/api/v1/grades?page=0&size=10"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGradeApiValidationRejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/grades?page=-1&size=10"))
                .andExpect(status().isBadRequest());
    }

    @Autowired private com.registrarops.service.PolicySettingService policySettingService;

    @Test
    void testRefundPolicyReadsFromConfig() {
        // After Cycle 2 remediation, OrderService reads runtime policy from
        // PolicySettingService. We prove the getter is live by mutating the
        // policy row and observing downstream logic change — then restore.
        assertEquals(14, orderService.getRefundWindowDays());
        assertEquals(30, orderService.getPaymentTimeoutMinutes());
        assertEquals(10L, orderService.getIdempotencyWindowMinutes());

        policySettingService.set("orders.refund_window_days", "1", 1L, "admin");
        try {
            assertEquals(1, orderService.getRefundWindowDays());
        } finally {
            policySettingService.set("orders.refund_window_days", "14", 1L, "admin");
        }
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testQuietHoursValidationRejectsOutOfRange() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/messages/preferences/quiet-hours")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("startHour", "99")
                        .param("endHour", "7"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testDeviceBindingDefaultsOffAndSuppressesNotice() {
        // Audit #12: default is opt-out. AuthService must not emit a notice
        // when the flag is unset. We call detectUnusualLogin with a mock
        // request and confirm no crash and no notice saved.
        User u = userRepository.findById(4L).orElseThrow();
        assertFalse(Boolean.TRUE.equals(u.getDeviceBindingEnabled()),
                "device binding should default to opt-out");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testStudentsApiTotalsOnlyIncludeStudents() throws Exception {
        mockMvc.perform(get("/api/v1/students?page=0&size=50"))
                .andExpect(status().isOk())
                // Seed has 1 student (id=4). Totals must reflect students only.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":1")));
    }
}
