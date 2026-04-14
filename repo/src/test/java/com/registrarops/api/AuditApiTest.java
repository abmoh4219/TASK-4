package com.registrarops.api;

import com.registrarops.entity.AuditLog;
import com.registrarops.repository.AuditLogRepository;
import com.registrarops.service.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuditApiTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuditService auditService;
    @Autowired private AuditLogRepository auditLogRepository;

    @Test
    void testAuditLogCreatedOnAction() {
        long before = auditLogRepository.count();
        auditService.logSystem("UNIT_TEST_ACTION", "Test", 1L,
                "{\"username\":\"x\",\"password\":\"secret\"}");
        long after = auditLogRepository.count();
        assertEquals(before + 1, after);
    }

    @Test
    void testAuditLogRepositoryHasNoUpdateOrDelete() {
        // Static-audit guarantee: AuditLogRepository extends Spring Data Repository
        // (NOT JpaRepository), so update/delete methods are not inherited. Verify by
        // reflection that the only mutator exposed is a single save() method.
        Method[] methods = AuditLogRepository.class.getMethods();
        long deleteMethods = Arrays.stream(methods)
                .filter(m -> m.getName().toLowerCase().contains("delete"))
                .count();
        long saveMethods = Arrays.stream(methods)
                .filter(m -> m.getName().equalsIgnoreCase("save"))
                .count();
        assertEquals(0, deleteMethods, "AuditLogRepository must NOT expose any delete methods");
        assertEquals(1, saveMethods, "AuditLogRepository must expose exactly one save() (insert-only)");
    }

    @Test
    void testSensitiveFieldsMaskedInAuditLog() {
        String input = "{\"username\":\"alice\",\"password\":\"hunter2!\",\"phone\":\"+1-555-0123\"}";
        String masked = AuditService.maskSensitive(input);
        assertTrue(masked.contains("[MASKED]"));
        assertFalse(masked.contains("hunter2"));
        assertFalse(masked.contains("0123"));
        assertTrue(masked.contains("alice"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminCanReadAuditLog() throws Exception {
        // Seed a row so the page has content.
        AuditLog a = auditService.logSystem("READ_TEST", "Test", 1L, "{}");
        assertNotNull(a.getId());
        mockMvc.perform(get("/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("IMMUTABLE AUDIT RECORD")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testStudentCannotReadAuditLog() throws Exception {
        mockMvc.perform(get("/admin/audit"))
                .andExpect(status().isForbidden());
    }
}
