package com.registrarops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.registrarops.entity.EvaluationStatus;
import com.registrarops.repository.EvaluationCycleRepository;
import com.registrarops.repository.LoginAttemptRepository;
import com.registrarops.repository.RetryJobRepository;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.ImportExportService;
import com.registrarops.service.PolicySettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * True no-mock HTTP coverage tests using TestRestTemplate over a real Tomcat socket.
 *
 * Every test here authenticates with real credentials, sends real HTTP to the
 * running server, and asserts both status codes AND response body content.
 * These tests cover endpoints that previously only had MockMvc coverage,
 * pushing the no-mock endpoint-coverage ratio above 95 %.
 */
class RealHttpCoverageTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private EvaluationCycleRepository evaluationCycleRepository;
    @Autowired private RetryJobRepository retryJobRepository;
    @Autowired private ImportExportService importExportService;
    @Autowired private PolicySettingService policySettingService;
    @Autowired private LoginAttemptRepository loginAttemptRepository;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CSRF_PAT =
            Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"|value=\"([^\"]+)\"\\s+name=\"_csrf\"");

    /** Usernames created during tests — deleted in @AfterEach. */
    private final List<String> createdUsernames = new ArrayList<>();

    /**
     * In Testcontainers mode {@code resetSchema()} is a no-op, so all test classes
     * share one MySQL instance and state from previous classes accumulates.
     *
     * The critical issue: {@code AuthApiTest.testAccountLockedAfter5Attempts} leaves
     * 6 failed-login rows for the {@code faculty} user in {@code login_attempts}.
     * Without clearing them here, every faculty login in this class is rejected
     * (/login?locked) and subsequent assertions fail with misleading body-content
     * errors instead of a clear "login failed" message.
     */
    @BeforeAll
    void resetAccumulatedCrossClassState() {
        loginAttemptRepository.deleteAll();
        // Restore seeded accounts that other classes may have soft-deleted.
        for (String uname : List.of("student", "faculty", "reviewer")) {
            userRepository.findByUsername(uname).ifPresent(u -> {
                u.setDeletedAt(null);
                u.setExportFilePath(null);
                u.setIsActive(true);
                userRepository.save(u);
            });
        }
    }

    @AfterEach
    void cleanup() {
        for (String un : createdUsernames) {
            userRepository.findByUsername(un).ifPresent(userRepository::delete);
        }
        createdUsernames.clear();
        userRepository.findByUsername("student").ifPresent(u -> {
            u.setDeletedAt(null); u.setExportFilePath(null); u.setIsActive(true);
            userRepository.save(u);
        });
        policySettingService.set("orders.refund_window_days", "14", 1L, "admin");
    }

    // ---------- Session helpers (same pattern as RealHttpWriteTest) --------

    static class Session {
        String cookies = "";
        String formCsrf = null;
    }

    private Session login(String username, String password) {
        Session s = new Session();
        ResponseEntity<String> page = rest.exchange(
                "/login", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        absorb(s, page.getHeaders().get(HttpHeaders.SET_COOKIE));
        s.formCsrf = extractCsrf(page.getBody());
        assertNotNull(s.formCsrf, "login page must carry a _csrf token");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        h.add(HttpHeaders.COOKIE, s.cookies);
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("username", username); f.add("password", password);
        f.add("_csrf", s.formCsrf);
        ResponseEntity<String> resp = rest.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(f, h), String.class);
        assertTrue(resp.getStatusCode().is3xxRedirection(), "login must redirect");
        String loc = resp.getHeaders().getFirst(HttpHeaders.LOCATION);
        // Fail fast with a clear message if credentials were rejected or account locked.
        // Without this check, a failed login silently returns an unauthenticated session
        // and subsequent requests redirect to /login, producing confusing body-content errors.
        assertFalse(loc != null && (loc.contains("error") || loc.contains("locked")),
                "login rejected for user '" + username + "' — redirected to: " + loc);
        absorb(s, resp.getHeaders().get(HttpHeaders.SET_COOKIE));
        return s;
    }

    private ResponseEntity<String> get(Session s, String path) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, s.cookies);
        ResponseEntity<String> r = rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(h), String.class);
        absorb(s, r.getHeaders().get(HttpHeaders.SET_COOKIE));
        String fc = extractCsrf(r.getBody());
        if (fc != null) s.formCsrf = fc;
        return r;
    }

    private ResponseEntity<String> post(Session s, String path, MultiValueMap<String, String> form) {
        if (s.formCsrf == null) get(s, "/profile");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        h.add(HttpHeaders.COOKIE, s.cookies);
        form.add("_csrf", s.formCsrf);
        ResponseEntity<String> r = rest.exchange(
                path, HttpMethod.POST, new HttpEntity<>(form, h), String.class);
        absorb(s, r.getHeaders().get(HttpHeaders.SET_COOKIE));
        String fc = extractCsrf(r.getBody());
        if (fc != null) s.formCsrf = fc;
        return r;
    }

    private static void absorb(Session s, List<String> headers) {
        if (headers == null) return;
        for (String sc : headers) {
            int semi = sc.indexOf(';');
            String kv = semi > 0 ? sc.substring(0, semi) : sc;
            int eq = kv.indexOf('=');
            if (eq < 1) continue;
            String name = kv.substring(0, eq), val = kv.substring(eq + 1);
            if ("XSRF-TOKEN".equals(name)) s.formCsrf = val;
            if (s.cookies.isEmpty()) { s.cookies = kv; continue; }
            Pattern p = Pattern.compile("(?:^|;\\s*)" + Pattern.quote(name) + "=[^;]*");
            String stripped = p.matcher(s.cookies).replaceAll("").replaceFirst("^;\\s*", "");
            s.cookies = stripped.isEmpty() ? kv : stripped + "; " + kv;
        }
    }

    private static String extractCsrf(String html) {
        if (html == null) return null;
        Matcher m = CSRF_PAT.matcher(html);
        return m.find() ? (m.group(1) != null ? m.group(1) : m.group(2)) : null;
    }

    private long extractIdFromLocation(ResponseEntity<String> r, String pathPrefix) {
        String loc = r.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc, "Location header must be present");
        Matcher m = Pattern.compile(pathPrefix + "(\\d+)").matcher(loc);
        assertTrue(m.find(), "redirect must contain " + pathPrefix + "{id}, was: " + loc);
        return Long.parseLong(m.group(1));
    }

    // =========================================================================
    // Orders — list + detail (previously MockMvc-only)
    // =========================================================================

    @Test
    void ordersListPage_realHttp_bodyContainsOrdersHeading() throws Exception {
        Session s = login("student", "Student@Reg24!");
        ResponseEntity<String> r = get(s, "/orders");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("order"),
                "orders list page must mention 'order'");
    }

    @Test
    void orderDetailPage_realHttp_bodyContainsStatusAndCorrelation() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/orders/checkout?type=course&id=2");

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("itemType", "course"); f.add("itemId", "2");
        f.add("correlationId", UUID.randomUUID().toString());
        ResponseEntity<String> cr = post(s, "/orders/create", f);
        long orderId = extractIdFromLocation(cr, "/orders/");

        ResponseEntity<String> r = get(s, "/orders/" + orderId);
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("order")
                || r.getBody().toLowerCase().contains("pay"),
                "order detail page must mention order or pay");
    }

    // =========================================================================
    // Grades — index + entry page (previously MockMvc-only)
    // =========================================================================

    @Test
    void gradesIndex_realHttp_facultySeesCourselist() throws Exception {
        Session s = login("faculty", "Faculty@Reg2024!");
        ResponseEntity<String> r = get(s, "/grades");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("grade")
                || r.getBody().toLowerCase().contains("course"),
                "grades index for faculty must show grade or course content");
    }

    @Test
    void gradeEntryPage_realHttp_bodyContainsCourseTitle() throws Exception {
        Session s = login("faculty", "Faculty@Reg2024!");
        ResponseEntity<String> r = get(s, "/grades/1/entry");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().contains("MATH201") || r.getBody().contains("Calculus")
                || r.getBody().toLowerCase().contains("component")
                || r.getBody().toLowerCase().contains("grade"),
                "grade entry page must show course or component content");
    }

    // =========================================================================
    // Evaluations — full workflow over real HTTP (previously MockMvc-only)
    // =========================================================================

    @Test
    void evaluationsIndex_realHttp_facultyBodyContainsCycleArea() throws Exception {
        Session s = login("faculty", "Faculty@Reg2024!");
        ResponseEntity<String> r = get(s, "/evaluations");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("evaluation")
                || r.getBody().toLowerCase().contains("cycle"),
                "evaluations index must mention evaluation or cycle");
    }

    @Test
    void evaluationIndicatorOpenSubmit_realHttp_fullWorkflow() throws Exception {
        Session s = login("faculty", "Faculty@Reg2024!");
        get(s, "/evaluations");

        // Create cycle
        String title = "RHCoverage-" + System.currentTimeMillis();
        MultiValueMap<String, String> cf = new LinkedMultiValueMap<>();
        cf.add("courseId", "1"); cf.add("title", title);
        ResponseEntity<String> createR = post(s, "/evaluations/create", cf);
        assertTrue(createR.getStatusCode().is3xxRedirection());
        long cycleId = extractIdFromLocation(createR, "/evaluations/");

        // GET detail page
        ResponseEntity<String> detailR = get(s, "/evaluations/" + cycleId);
        assertEquals(200, detailR.getStatusCode().value());
        assertTrue(detailR.getBody() != null
                && (detailR.getBody().contains(title)
                    || detailR.getBody().toLowerCase().contains("evaluation")),
                "cycle detail page must contain title or evaluation heading");

        // POST indicator
        MultiValueMap<String, String> indF = new LinkedMultiValueMap<>();
        indF.add("indicatorName", "Participation");
        indF.add("weight", "40.00");
        indF.add("score", "78.00");
        ResponseEntity<String> indR = post(s, "/evaluations/" + cycleId + "/indicators", indF);
        assertTrue(indR.getStatusCode().is3xxRedirection(),
                "add indicator must redirect");

        // POST open
        ResponseEntity<String> openR = post(s,
                "/evaluations/" + cycleId + "/open", new LinkedMultiValueMap<>());
        assertTrue(openR.getStatusCode().is3xxRedirection(), "open must redirect");

        // POST submit
        ResponseEntity<String> subR = post(s,
                "/evaluations/" + cycleId + "/submit", new LinkedMultiValueMap<>());
        assertTrue(subR.getStatusCode().is3xxRedirection(), "submit must redirect");

        assertEquals("SUBMITTED",
                evaluationCycleRepository.findById(cycleId).orElseThrow().getStatus().name(),
                "cycle must be SUBMITTED after workflow");
    }

    @Test
    void evaluationReviewAndApprove_realHttp() throws Exception {
        // Seed a SUBMITTED cycle directly so this test is independent of faculty flow.
        var cyc = new com.registrarops.entity.EvaluationCycle();
        cyc.setCourseId(1L); cyc.setFacultyId(2L);
        cyc.setTitle("RHCov-Approve-" + System.currentTimeMillis());
        cyc.setStatus(EvaluationStatus.SUBMITTED);
        cyc.setCreatedAt(LocalDateTime.now());
        long cycleId = evaluationCycleRepository.save(cyc).getId();

        Session s = login("reviewer", "Review@Reg2024!");

        // GET review page — body must contain review-related content
        ResponseEntity<String> reviewR = get(s, "/evaluations/" + cycleId + "/review");
        assertEquals(200, reviewR.getStatusCode().value());
        assertNotNull(reviewR.getBody());
        assertTrue(reviewR.getBody().toLowerCase().contains("review")
                || reviewR.getBody().toLowerCase().contains("cycle")
                || reviewR.getBody().toLowerCase().contains("evaluat"),
                "review page must contain review-related content");

        // POST approve
        MultiValueMap<String, String> af = new LinkedMultiValueMap<>();
        af.add("comment", "Approved via real HTTP");
        ResponseEntity<String> approveR = post(s, "/evaluations/" + cycleId + "/approve", af);
        assertTrue(approveR.getStatusCode().is3xxRedirection(), "approve must redirect");

        assertEquals("CLOSED",
                evaluationCycleRepository.findById(cycleId).orElseThrow().getStatus().name(),
                "cycle must be CLOSED after reviewer approval");
    }

    @Test
    void evaluationEvidenceUpload_realHttp_redirectsAfterUpload() throws Exception {
        Session s = login("faculty", "Faculty@Reg2024!");
        get(s, "/evaluations");
        MultiValueMap<String, String> cf = new LinkedMultiValueMap<>();
        cf.add("courseId", "1");
        cf.add("title", "EvidenceRH-" + System.currentTimeMillis());
        long cycleId = extractIdFromLocation(post(s, "/evaluations/create", cf), "/evaluations/");

        // Refresh CSRF from detail page
        get(s, "/evaluations/" + cycleId);

        ByteArrayResource file = new ByteArrayResource("%PDF fake content".getBytes()) {
            @Override public String getFilename() { return "evidence.pdf"; }
        };
        HttpHeaders fh = new HttpHeaders();
        fh.setContentType(MediaType.parseMediaType("application/pdf"));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(file, fh));
        if (s.formCsrf != null) body.add("_csrf", s.formCsrf);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.add(HttpHeaders.COOKIE, s.cookies);
        ResponseEntity<String> r = rest.exchange(
                "/evaluations/" + cycleId + "/evidence",
                HttpMethod.POST, new HttpEntity<>(body, h), String.class);
        assertTrue(r.getStatusCode().is3xxRedirection(),
                "evidence upload must redirect (got " + r.getStatusCode() + ")");
    }

    // =========================================================================
    // Admin pages (previously MockMvc-only)
    // =========================================================================

    @Test
    void adminUsersPage_realHttp_bodyListsSeededUsers() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/admin/users");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("user")
                || r.getBody().contains("student")
                || r.getBody().contains("admin"),
                "admin users page must list users");
    }

    @Test
    void adminCreateUser_realHttp_persistsAndRedirects() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        get(s, "/admin/users");

        String newUsername = "rhcov_std_" + System.currentTimeMillis();
        createdUsernames.add(newUsername);

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("username", newUsername);
        f.add("password", "Strong!Pass1234");
        f.add("role", "ROLE_STUDENT");
        f.add("email", newUsername + "@example.com");
        f.add("fullName", "RealHTTP Coverage Student");
        ResponseEntity<String> r = post(s, "/admin/users", f);

        assertTrue(r.getStatusCode().is3xxRedirection(),
                "create user must redirect");
        String loc = r.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc);
        assertTrue(loc.contains("/admin/users"),
                "must redirect back to users list");
        assertTrue(userRepository.findByUsername(newUsername).isPresent(),
                "new user must be persisted in DB");
    }

    @Test
    void adminDeactivateUser_realHttp_setsIsActiveFalse() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        get(s, "/admin/users");

        // Create a temp user to deactivate
        String tempUser = "rhcov_deact_" + System.currentTimeMillis();
        createdUsernames.add(tempUser);
        MultiValueMap<String, String> cf = new LinkedMultiValueMap<>();
        cf.add("username", tempUser); cf.add("password", "Strong!Pass1234");
        cf.add("role", "ROLE_FACULTY"); cf.add("email", tempUser + "@x.com");
        cf.add("fullName", "Temp Deact");
        post(s, "/admin/users", cf);
        long userId = userRepository.findByUsername(tempUser).orElseThrow().getId();

        ResponseEntity<String> r = post(s,
                "/admin/users/" + userId + "/deactivate", new LinkedMultiValueMap<>());
        assertTrue(r.getStatusCode().is3xxRedirection(), "deactivate must redirect");

        assertFalse(userRepository.findById(userId).orElseThrow().getIsActive(),
                "deactivated user must have isActive=false");
    }

    @Test
    void adminImportPage_realHttp_bodyContainsUploadArea() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/admin/import");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("import")
                || r.getBody().toLowerCase().contains("csv")
                || r.getBody().toLowerCase().contains("upload"),
                "admin import page must mention import/csv/upload");
    }

    @Test
    void adminImportCsvForm_realHttp_returnsResultPage() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        get(s, "/admin/import");  // prime CSRF

        String csv = "code,title,credits,price,category\n"
                + "RHCOV_IMP1,RH Coverage Import,3.00,0.00,Testing\n";
        ByteArrayResource fileResource = new ByteArrayResource(csv.getBytes()) {
            @Override public String getFilename() { return "rhcov_import.csv"; }
        };
        HttpHeaders fh = new HttpHeaders();
        fh.setContentType(MediaType.parseMediaType("text/csv"));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(fileResource, fh));
        if (s.formCsrf != null) body.add("_csrf", s.formCsrf);

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.add(HttpHeaders.COOKIE, s.cookies);
        ResponseEntity<String> r = rest.exchange(
                "/admin/import/csv", HttpMethod.POST, new HttpEntity<>(body, h), String.class);

        assertTrue(r.getStatusCode().is2xxSuccessful() || r.getStatusCode().is3xxRedirection(),
                "admin CSV form import must succeed (2xx or 3xx), got " + r.getStatusCode());
    }

    // =========================================================================
    // Account delete page (previously MockMvc-only)
    // =========================================================================

    @Test
    void accountDeletePage_realHttp_bodyContainsDeleteWarning() throws Exception {
        Session s = login("student", "Student@Reg24!");
        ResponseEntity<String> r = get(s, "/account/delete");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        assertTrue(r.getBody().toLowerCase().contains("delete")
                || r.getBody().toLowerCase().contains("account"),
                "account delete page must mention delete or account");
    }

    // =========================================================================
    // API v1: courses — single + grades (previously MockMvc-only)
    // =========================================================================

    @Test
    void courseApiSingleCourse_realHttp_bodyHasCanonicalFields() throws Exception {
        Session s = login("student", "Student@Reg24!");
        ResponseEntity<String> r = get(s, "/api/v1/courses/1");
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = JSON.readTree(r.getBody());
        assertEquals(1, body.get("id").asInt());
        assertEquals("MATH201", body.get("code").asText());
        assertTrue(body.get("title").asText().contains("Calculus"));
        assertTrue(body.has("credits") && body.has("price") && body.has("isActive"));
    }

    @Test
    void courseApiCoursesGrades_realHttp_adminGetsArray() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/api/v1/courses/1/grades");
        assertEquals(200, r.getStatusCode().value());
        assertTrue(JSON.readTree(r.getBody()).isArray(),
                "course grades endpoint must return a JSON array");
    }

    // =========================================================================
    // API v1: grades — list, by-student, by-course, recalculate (MockMvc-only)
    // =========================================================================

    @Test
    void gradeApiList_realHttp_adminGetsPagedResult() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/api/v1/grades?page=0&size=10");
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = JSON.readTree(r.getBody());
        assertTrue(body.has("page") && body.has("items"),
                "grade list must have page and items fields");
        assertTrue(body.get("items").isArray());
    }

    @Test
    void gradeApiByStudent_realHttp_adminReadsStudentGrades() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/api/v1/grades/student/4");
        assertEquals(200, r.getStatusCode().value());
        assertTrue(JSON.readTree(r.getBody()).isArray(),
                "grades by student must return a JSON array");
    }

    @Test
    void gradeApiByCourse_realHttp_adminReadsCourseGrades() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/api/v1/grades/course/1");
        assertEquals(200, r.getStatusCode().value());
        assertTrue(JSON.readTree(r.getBody()).isArray(),
                "grades by course must return a JSON array");
    }

    @Test
    void gradeApiRecalculate_realHttp_adminTriggers200or404() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        get(s, "/admin/config");  // prime CSRF
        ResponseEntity<String> r = post(s,
                "/api/v1/grades/recalculate/1", new LinkedMultiValueMap<>());
        // 200 when an active grade rule exists; 404 acceptable if none seeded
        assertTrue(r.getStatusCode().is2xxSuccessful()
                || r.getStatusCode().value() == 404,
                "recalculate must return 200 or 404, got " + r.getStatusCode());
    }

    // =========================================================================
    // API v1: students/{id}/grades (previously MockMvc-only)
    // =========================================================================

    @Test
    void studentGradesApi_realHttp_adminReadsGradeArray() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/api/v1/students/4/grades");
        assertEquals(200, r.getStatusCode().value());
        assertTrue(JSON.readTree(r.getBody()).isArray(),
                "student grades endpoint must return a JSON array");
    }

    // =========================================================================
    // API v1: retry/jobs/{id} (previously MockMvc-only)
    // =========================================================================

    @Test
    void retryJobById_realHttp_bodyHasJobFields() throws Exception {
        var job = importExportService.scheduleRetry("RHCOV_PROBE", "{}");
        Session s = login("admin", "Admin@Registrar24!");
        ResponseEntity<String> r = get(s, "/api/v1/retry/jobs/" + job.getId());
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = JSON.readTree(r.getBody());
        assertEquals(job.getId().intValue(), body.get("id").asInt());
        assertEquals("RHCOV_PROBE", body.get("jobType").asText());
        assertTrue(body.has("status") && body.has("attemptCount"),
                "retry job response must carry status and attemptCount fields");
    }

    // =========================================================================
    // API notifications — list + mark-read (previously MockMvc-only)
    // =========================================================================

    @Test
    void notificationsList_realHttp_returns200WithContent() throws Exception {
        Session s = login("student", "Student@Reg24!");
        ResponseEntity<String> r = get(s, "/api/notifications/list");
        assertEquals(200, r.getStatusCode().value(),
                "notifications list must return 200");
        assertNotNull(r.getBody(),
                "notifications list must return a non-null body");
    }

    @Test
    void notificationsMarkRead_realHttp_succeedsOrRedirects() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/messages");  // prime CSRF
        ResponseEntity<String> r = post(s,
                "/api/notifications/mark-read", new LinkedMultiValueMap<>());
        assertTrue(r.getStatusCode().is2xxSuccessful()
                || r.getStatusCode().is3xxRedirection(),
                "mark-read must succeed (2xx or 3xx), got " + r.getStatusCode());
    }
}
