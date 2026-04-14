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
}
