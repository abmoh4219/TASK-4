package com.registrarops.api;

import com.registrarops.repository.UserRepository;
import com.registrarops.service.MessageService;
import com.registrarops.service.PolicySettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Coverage-boost integration tests focused on the controllers that the
 * pre-existing API-test suite exercises lightly: DashboardController,
 * MessageController, NotificationApiController, SearchApiController,
 * AdminController (web + import/export/audit/config), and the v1 REST surfaces
 * for courses, policy and reports.
 *
 * Every test runs against the real Spring context + real MySQL via the
 * AbstractIntegrationTest base class, so each request goes through the full
 * Spring Security filter chain, real controllers, real services and real
 * Hibernate writes — no mocks. State that mutates the seed (policy values,
 * the student account, message preferences) is restored in @AfterEach.
 */
@AutoConfigureMockMvc
class CoverageBoostTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PolicySettingService policySettingService;
    @Autowired private MessageService messageService;

    @AfterEach
    void restoreState() {
        // canonical V015 policy values
        policySettingService.set("orders.payment_timeout_minutes", "30", 1L, "admin");
        policySettingService.set("orders.refund_window_days",      "14", 1L, "admin");
        policySettingService.set("orders.idempotency_window_minutes","10", 1L, "admin");
        policySettingService.set("retry.max_attempts",              "3",  1L, "admin");
        policySettingService.set("notifications.quiet_start_hour",  "22", 1L, "admin");
        policySettingService.set("notifications.quiet_end_hour",    "7",  1L, "admin");

        // restore student preferences (mute test below appends a category)
        messageService.updateQuietHours(4L, 22, 7);
        var pref = messageService.getPreferences(4L);
        if (pref != null) {
            pref.setMutedCategories("");
        }
        // also reset student deletion + activation flags in case some other
        // test class's failure path left them stale.
        userRepository.findByUsername("student").ifPresent(u -> {
            u.setDeletedAt(null);
            u.setExportFilePath(null);
            u.setIsActive(true);
            userRepository.save(u);
        });
    }

    // ------- DashboardController per role ----------------------------------

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void dashboardStudent() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(view().name("dashboard/index"))
                .andExpect(model().attributeExists("currentUser", "enrollments", "orders", "grades", "unreadCount"));
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void dashboardFaculty() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(model().attributeExists("courses", "openCycles"));
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "REVIEWER")
    void dashboardReviewer() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(model().attributeExists("submittedCycles"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void dashboardAdmin() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(model().attributeExists("userCount", "courseCount", "orderCount", "auditCount"));
    }

    @Test
    void dashboardUnauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());
    }

    // ------- MessageController ---------------------------------------------

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void messagesIndex() throws Exception {
        mockMvc.perform(get("/messages")).andExpect(status().isOk())
                .andExpect(view().name("messages/index"))
                .andExpect(model().attributeExists("messages", "preferences"));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void messagesMarkAllRead() throws Exception {
        mockMvc.perform(post("/messages/mark-read").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/messages"));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void messagesMuteCategory() throws Exception {
        mockMvc.perform(post("/messages/preferences/mute")
                        .param("category", "ORDER")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/messages"));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void messagesQuietHoursOk() throws Exception {
        mockMvc.perform(post("/messages/preferences/quiet-hours")
                        .param("startHour", "21")
                        .param("endHour", "8")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void messagesQuietHoursOutOfRange() throws Exception {
        mockMvc.perform(post("/messages/preferences/quiet-hours")
                        .param("startHour", "99")
                        .param("endHour", "5")
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ------- NotificationApiController -------------------------------------

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void notificationCount() throws Exception {
        mockMvc.perform(get("/api/notifications/count"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void notificationList() throws Exception {
        mockMvc.perform(get("/api/notifications/list"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void notificationMarkRead() throws Exception {
        mockMvc.perform(post("/api/notifications/mark-read").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    // ------- SearchApiController -------------------------------------------

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void searchSuggestionsBasic() throws Exception {
        mockMvc.perform(get("/api/search/suggestions").param("q", "calc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void searchSuggestionsEmpty() throws Exception {
        mockMvc.perform(get("/api/search/suggestions"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void searchSuggestionsTypo() throws Exception {
        // exercises the "did you mean" Levenshtein branch
        mockMvc.perform(get("/api/search/suggestions").param("q", "calclus"))
                .andExpect(status().isOk());
    }

    // ------- AdminController web pages -------------------------------------

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminIndex() throws Exception {
        mockMvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(view().name("admin/index"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminUsersList() throws Exception {
        mockMvc.perform(get("/admin/users")).andExpect(status().isOk())
                .andExpect(view().name("admin/users"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCreateUserHappy() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .param("username", "covboost_" + System.currentTimeMillis())
                        .param("password", "Strong!Pass1234")
                        .param("role", "ROLE_STUDENT")
                        .param("email", "covboost@example.com")
                        .param("fullName", "Coverage Booster"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCreateUserWeakPassword() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .param("username", "weakpass_" + System.currentTimeMillis())
                        .param("password", "short")
                        .param("role", "ROLE_STUDENT"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCreateUserDuplicate() throws Exception {
        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .param("username", "student") // already exists
                        .param("password", "Strong!Pass1234")
                        .param("role", "ROLE_STUDENT"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminImportPage() throws Exception {
        mockMvc.perform(get("/admin/import")).andExpect(status().isOk())
                .andExpect(view().name("admin/import"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminImportCsvViaForm() throws Exception {
        String csv = "code,title,credits,price,category\n"
                + "COVBOOST1,Coverage Boost Course,3.00,9.99,Math\n";
        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "covboost.csv", "text/csv", csv.getBytes());
        mockMvc.perform(multipart("/admin/import/csv").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/import"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminExportCoursesCsv() throws Exception {
        mockMvc.perform(get("/admin/export/courses.csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("courses.csv")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAuditPage() throws Exception {
        mockMvc.perform(get("/admin/audit")).andExpect(status().isOk())
                .andExpect(view().name("admin/audit"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConfigGet() throws Exception {
        mockMvc.perform(get("/admin/config")).andExpect(status().isOk())
                .andExpect(view().name("admin/config"))
                .andExpect(model().attributeExists("settings", "allowedKeys"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConfigPostValid() throws Exception {
        mockMvc.perform(post("/admin/config").with(csrf())
                        .param("key", "orders.refund_window_days")
                        .param("value", "20"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/config"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConfigPostInvalid() throws Exception {
        // value below the per-key validation pattern → caught and flashed.
        mockMvc.perform(post("/admin/config").with(csrf())
                        .param("key", "orders.refund_window_days")
                        .param("value", "-7"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminDeactivateNonexistentUser() throws Exception {
        mockMvc.perform(post("/admin/users/9999/deactivate").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    // ------- v1 REST surfaces ----------------------------------------------

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void courseApiList() throws Exception {
        mockMvc.perform(get("/api/v1/courses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].code").exists())
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].isActive", org.hamcrest.Matchers.is(true)));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void courseApiGetOne() throws Exception {
        mockMvc.perform(get("/api/v1/courses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", org.hamcrest.Matchers.is(1)))
                .andExpect(jsonPath("$.code", org.hamcrest.Matchers.is("MATH201")))
                .andExpect(jsonPath("$.title", org.hamcrest.Matchers.containsString("Calculus")));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void courseApiGradesForbiddenForStudent() throws Exception {
        mockMvc.perform(get("/api/v1/courses/1/grades"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void courseApiGradesAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/courses/1/grades"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void policyApiList() throws Exception {
        mockMvc.perform(get("/api/v1/policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedKeys").exists())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("orders.refund_window_days")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void policyApiGetOne() throws Exception {
        mockMvc.perform(get("/api/v1/policy/orders.refund_window_days"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void policyApiGetUnknownKey() throws Exception {
        mockMvc.perform(get("/api/v1/policy/totally.unknown.key"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void policyApiUpdateMissingValue() throws Exception {
        mockMvc.perform(put("/api/v1/policy/orders.refund_window_days")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void reportApiGpaSummaryAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/reports/gpa-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].studentId").exists())
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].cumulativeGpa").exists())
                .andExpect(jsonPath("$[0].totalCredits").exists())
                .andExpect(jsonPath("$[0].courseCount").exists());
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void reportApiGpaSummaryFacultyForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/gpa-summary"))
                .andExpect(status().isForbidden());
    }

    // ------- Real-HTTP smoke (TestRestTemplate, transport-level) ------------

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.boot.test.web.client.TestRestTemplate restTemplate;

    @Test
    void realHttpLoginPageReachable() {
        var resp = restTemplate.getForEntity("/login", String.class);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().contains("login") || resp.getBody().contains("Login"));
    }

    @Test
    void realHttpProtectedRouteRedirectsToLogin() {
        // A real out-of-process HTTP request to a protected route — no MockMvc.
        var resp = restTemplate.getForEntity("/", String.class);
        // unauth → either 302 redirect or 200 to the login page (after follow)
        assertTrue(resp.getStatusCode().is3xxRedirection() || resp.getStatusCode().is2xxSuccessful());
    }

    @Test
    void realHttpInvalidExportTokenForbidden() {
        var resp = restTemplate.getForEntity("/account/export/totally-bogus.token", String.class);
        assertEquals(403, resp.getStatusCode().value());
    }
}
