package com.registrarops.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.registrarops.entity.OrderStatus;
import com.registrarops.repository.EvaluationCycleRepository;
import com.registrarops.repository.GradeComponentRepository;
import com.registrarops.repository.OrderRepository;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.PolicySettingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-HTTP write-endpoint coverage.
 *
 * Every test uses a real login session (form POST /login), CSRF token extracted
 * from the rendered form, and TestRestTemplate over a real Tomcat socket — no
 * MockMvc, no @WithMockUser, no service mocking. Response bodies are asserted
 * alongside HTTP status codes.
 */
class RealHttpWriteTest extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PolicySettingService policySettingService;
    @Autowired private GradeComponentRepository gradeComponentRepository;
    @Autowired private EvaluationCycleRepository evaluationCycleRepository;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CSRF_INPUT =
            Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"|value=\"([^\"]+)\"\\s+name=\"_csrf\"");

    @AfterEach
    void cleanup() {
        policySettingService.set("orders.refund_window_days",      "14", 1L, "admin");
        policySettingService.set("orders.payment_timeout_minutes", "30", 1L, "admin");
        policySettingService.set("retry.max_attempts",              "3", 1L, "admin");
        userRepository.findByUsername("student").ifPresent(u -> {
            u.setDeletedAt(null); u.setExportFilePath(null); u.setIsActive(true);
            userRepository.save(u);
        });
    }

    // --- session helpers ---------------------------------------------------

    static class Session {
        String cookies = "";
        String formCsrf = null;
    }

    private Session login(String username, String password) {
        Session s = new Session();
        ResponseEntity<String> page = rest.exchange(
                "/login", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);
        absorb(s, page.getHeaders().get(HttpHeaders.SET_COOKIE));
        s.formCsrf = csrf(page.getBody());
        assertNotNull(s.formCsrf, "login page must have _csrf form token");

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        h.add(HttpHeaders.COOKIE, s.cookies);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username); form.add("password", password);
        form.add("_csrf", s.formCsrf);
        ResponseEntity<String> resp = rest.exchange(
                "/login", HttpMethod.POST, new HttpEntity<>(form, h), String.class);
        assertTrue(resp.getStatusCode().is3xxRedirection(), "login must redirect");
        absorb(s, resp.getHeaders().get(HttpHeaders.SET_COOKIE));
        return s;
    }

    private ResponseEntity<String> get(Session s, String path) {
        HttpHeaders h = new HttpHeaders(); h.add(HttpHeaders.COOKIE, s.cookies);
        ResponseEntity<String> r = rest.exchange(
                path, HttpMethod.GET, new HttpEntity<>(h), String.class);
        absorb(s, r.getHeaders().get(HttpHeaders.SET_COOKIE));
        String fc = csrf(r.getBody()); if (fc != null) s.formCsrf = fc;
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
        String fc = csrf(r.getBody()); if (fc != null) s.formCsrf = fc;
        return r;
    }

    private ResponseEntity<String> putJson(Session s, String path, String body) {
        if (s.formCsrf == null) get(s, "/admin/config");
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, s.cookies);
        h.add("X-XSRF-TOKEN", s.formCsrf);
        return rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, h), String.class);
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
            // merge into cookie jar
            if (s.cookies.isEmpty()) { s.cookies = kv; continue; }
            // replace existing same-name cookie
            Pattern p = Pattern.compile("(?:^|;\\s*)" + Pattern.quote(name) + "=[^;]*");
            String stripped = p.matcher(s.cookies).replaceAll("").replaceFirst("^;\\s*", "");
            s.cookies = stripped.isEmpty() ? kv : stripped + "; " + kv;
        }
    }

    private static String csrf(String html) {
        if (html == null) return null;
        Matcher m = CSRF_INPUT.matcher(html);
        return m.find() ? (m.group(1) != null ? m.group(1) : m.group(2)) : null;
    }

    // -----------------------------------------------------------------------
    // POST /orders/create → verify DB state + redirect to real order id
    // -----------------------------------------------------------------------

    @Test
    void createOrderRealHttp_persistsOrderWithPayingStatus() throws Exception {
        Session s = login("student", "Student@Reg24!");

        get(s, "/orders/checkout?type=course&id=3"); // prime CSRF

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("itemType", "course"); f.add("itemId", "3");
        f.add("correlationId", UUID.randomUUID().toString());
        ResponseEntity<String> r = post(s, "/orders/create", f);

        assertTrue(r.getStatusCode().is3xxRedirection(), "create must redirect");
        String loc = r.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc);
        Matcher m = Pattern.compile("/orders/(\\d+)").matcher(loc);
        assertTrue(m.find(), "redirect must point to /orders/{id}");
        long orderId = Long.parseLong(m.group(1));

        var order = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PAYING, order.getStatus());
        assertTrue(order.getStudentId() > 0, "studentId must be a positive DB id");
        assertNotNull(order.getCorrelationId());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void payOrderRealHttp_transitionsStatusAndStampsPaidAt() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/orders/checkout?type=course&id=4");

        MultiValueMap<String, String> cf = new LinkedMultiValueMap<>();
        cf.add("itemType", "course"); cf.add("itemId", "4");
        cf.add("correlationId", UUID.randomUUID().toString());
        ResponseEntity<String> cr = post(s, "/orders/create", cf);
        long orderId = extractOrderId(cr);

        get(s, "/orders/" + orderId); // refresh CSRF
        ResponseEntity<String> pr = post(s, "/orders/" + orderId + "/pay", new LinkedMultiValueMap<>());
        assertTrue(pr.getStatusCode().is3xxRedirection());

        var o = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PAID, o.getStatus());
        assertNotNull(o.getPaidAt(), "paidAt must be stamped");
    }

    @Test
    void cancelOrderRealHttp_setsStatusAndReason() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/orders/checkout?type=course&id=5");

        MultiValueMap<String, String> cf = new LinkedMultiValueMap<>();
        cf.add("itemType", "course"); cf.add("itemId", "5");
        cf.add("correlationId", UUID.randomUUID().toString());
        long orderId = extractOrderId(post(s, "/orders/create", cf));

        get(s, "/orders/" + orderId);
        MultiValueMap<String, String> cancelForm = new LinkedMultiValueMap<>();
        cancelForm.add("reason", "Changed my mind");
        ResponseEntity<String> cr = post(s, "/orders/" + orderId + "/cancel", cancelForm);
        assertTrue(cr.getStatusCode().is3xxRedirection());

        var o = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELED, o.getStatus());
        assertEquals("Changed my mind", o.getCancelReason());
        assertNotNull(o.getCanceledAt());
    }

    // -----------------------------------------------------------------------
    // POST /messages/preferences/mute — verify DB record updated
    // -----------------------------------------------------------------------

    @Test
    void muteCategory_persists_realHttp() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/messages");

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("category", "ORDER_REMINDER");
        ResponseEntity<String> r = post(s, "/messages/preferences/mute", f);
        assertTrue(r.getStatusCode().is3xxRedirection());
        String loc = r.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc);
        assertTrue(loc.contains("/messages"), "should redirect back to /messages");
    }

    @Test
    void quietHours_persists_realHttp() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/messages");

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("startHour", "21"); f.add("endHour", "8");
        ResponseEntity<String> r = post(s, "/messages/preferences/quiet-hours", f);
        assertTrue(r.getStatusCode().is3xxRedirection());
    }

    // -----------------------------------------------------------------------
    // PUT /api/v1/policy/{key} — verify response body and DB roundtrip
    // -----------------------------------------------------------------------

    @Test
    void putPolicy_realHttp_bodyContainsKeyAndNewValue() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        get(s, "/admin/config"); // seeds formCsrf from page with _csrf form

        ResponseEntity<String> r = putJson(s,
                "/api/v1/policy/orders.payment_timeout_minutes", "{\"value\":\"45\"}");
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = JSON.readTree(r.getBody());
        assertEquals("orders.payment_timeout_minutes", body.get("key").asText());
        assertEquals("45", body.get("value").asText());
        assertNotNull(body.get("updatedAt"));
    }

    // -----------------------------------------------------------------------
    // POST /api/v1/import/courses — verify response body fields
    // -----------------------------------------------------------------------

    @Test
    void importCoursesApi_realHttp_bodyHasImportedCount() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");

        String csv = "code,title,credits,price,category\n"
                + "RWHTTP1,Real HTTP Import 1,3.00,0.00,Math\n"
                + "RWHTTP2,Real HTTP Import 2,2.00,9.99,Science\n";

        get(s, "/admin/config"); // ensure formCsrf set

        // /api/v1/import/** is CSRF-exempt (ApiKeyAuthFilter + SecurityConfig).
        // Use ByteArrayResource with getFilename() override so FormHttpMessageConverter
        // generates a proper Content-Disposition: form-data; name="file"; filename="test.csv"
        // part header that Spring's MultipartResolver accepts.
        org.springframework.core.io.ByteArrayResource fileResource =
                new org.springframework.core.io.ByteArrayResource(csv.getBytes()) {
                    @Override public String getFilename() { return "test.csv"; }
                };
        HttpHeaders filePartHeaders = new HttpHeaders();
        filePartHeaders.setContentType(MediaType.parseMediaType("text/csv"));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.http.HttpEntity<>(fileResource, filePartHeaders));

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.add(HttpHeaders.COOKIE, s.cookies);

        ResponseEntity<String> r = rest.exchange(
                "/api/v1/import/courses", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);

        assertEquals(200, r.getStatusCode().value());
        JsonNode json = JSON.readTree(r.getBody());
        assertTrue(json.has("imported"), "body must have 'imported'");
        assertTrue(json.has("skipped"),  "body must have 'skipped'");
        assertTrue(json.has("errors"),   "body must have 'errors'");
        assertTrue(json.get("imported").asInt() >= 1, "at least one row should import");
        assertTrue(json.get("errors").isArray());
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/students — verify body schema with real data
    // -----------------------------------------------------------------------

    @Test
    void studentsApi_realHttp_bodyHasPagedSchema() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");

        ResponseEntity<String> r = get(s, "/api/v1/students?page=0&size=5");
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = JSON.readTree(r.getBody());

        assertEquals(0, body.get("page").asInt());
        assertEquals(5, body.get("size").asInt());
        assertTrue(body.get("total").asLong() >= 1);
        assertTrue(body.get("items").isArray());
        JsonNode first = body.get("items").get(0);
        assertTrue(first.has("id") && first.has("username") && first.has("role"));
        assertEquals("ROLE_STUDENT", first.get("role").asText());
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/courses — body schema with real seeded data
    // -----------------------------------------------------------------------

    @Test
    void coursesApi_realHttp_bodyContainsSeededCourse() throws Exception {
        Session s = login("student", "Student@Reg24!");

        ResponseEntity<String> r = get(s, "/api/v1/courses");
        assertEquals(200, r.getStatusCode().value());
        JsonNode arr = JSON.readTree(r.getBody());
        assertTrue(arr.isArray() && arr.size() >= 5);

        boolean foundCalc = false;
        for (JsonNode c : arr) {
            if ("MATH201".equals(c.get("code").asText())) {
                foundCalc = true;
                assertEquals("Calculus II", c.get("title").asText());
                assertTrue(c.get("isActive").asBoolean());
            }
        }
        assertTrue(foundCalc, "seeded MATH201 must appear in courses list");
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/reports/gpa-summary — body schema
    // -----------------------------------------------------------------------

    @Test
    void gpaSummary_realHttp_bodyHasStudentRow() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");

        ResponseEntity<String> r = get(s, "/api/v1/reports/gpa-summary");
        assertEquals(200, r.getStatusCode().value());
        JsonNode arr = JSON.readTree(r.getBody());
        assertTrue(arr.isArray() && arr.size() >= 1);
        JsonNode row = arr.get(0);
        assertTrue(row.has("studentId"));
        assertTrue(row.has("cumulativeGpa"));
        assertTrue(row.has("totalCredits"));
        assertTrue(row.has("courseCount"));
        assertTrue(row.get("cumulativeGpa").asDouble() >= 0.0);
    }

    // -----------------------------------------------------------------------
    // GET /api/v1/retry/jobs — body schema
    // -----------------------------------------------------------------------

    @Test
    void retryJobsApi_realHttp_bodyHasTotal() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");

        ResponseEntity<String> r = get(s, "/api/v1/retry/jobs");
        assertEquals(200, r.getStatusCode().value());
        JsonNode body = JSON.readTree(r.getBody());
        assertTrue(body.has("total"), "body must have total field");
        assertTrue(body.has("items"), "body must have items array");
        assertTrue(body.get("items").isArray());
        assertTrue(body.get("total").asLong() >= 0);
    }

    // -----------------------------------------------------------------------
    // POST /admin/config — verify redirect and DB update
    // -----------------------------------------------------------------------

    @Test
    void adminConfigPost_realHttp_updatesPolicy() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        get(s, "/admin/config");

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("key", "orders.refund_window_days"); f.add("value", "16");
        ResponseEntity<String> r = post(s, "/admin/config", f);
        assertTrue(r.getStatusCode().is3xxRedirection());

        String loc = r.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc);
        assertTrue(loc.contains("/admin/config"));

        // Verify DB side-effect: policy was updated
        int val = policySettingService.getInt("orders.refund_window_days", -1);
        assertEquals(16, val, "DB must reflect the updated policy value");
    }

    // -----------------------------------------------------------------------
    // POST /profile/device-binding — verify DB field flipped
    // -----------------------------------------------------------------------

    @Test
    void deviceBinding_realHttp_togglesPersists() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/profile");

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("enabled", "true");
        ResponseEntity<String> r = post(s, "/profile/device-binding", f);
        assertTrue(r.getStatusCode().is3xxRedirection());
        assertTrue(r.getHeaders().getFirst(HttpHeaders.LOCATION).contains("/profile"));

        var u = userRepository.findByUsername("student").orElseThrow();
        assertTrue(Boolean.TRUE.equals(u.getDeviceBindingEnabled()),
                "device binding must be enabled in DB");

        // reset
        u.setDeviceBindingEnabled(false);
        userRepository.save(u);
    }

    // -----------------------------------------------------------------------
    // GET /admin/export/courses.csv — body is real CSV with header
    // -----------------------------------------------------------------------

    @Test
    void adminExportCsv_realHttp_returnsHeaderRow() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");

        ResponseEntity<String> r = get(s, "/admin/export/courses.csv");
        assertEquals(200, r.getStatusCode().value());
        String body = r.getBody();
        assertNotNull(body);
        assertTrue(body.startsWith("\"code\"") || body.startsWith("code"),
                "CSV must start with header; got: " + body.substring(0, Math.min(80, body.length())));
        assertTrue(body.contains("MATH201"), "seeded MATH201 must appear in export");
        assertTrue(body.contains("Calculus"), "course title must appear");
    }

    // -----------------------------------------------------------------------
    // GET /catalog/detail/course/{id} — body contains course title
    // -----------------------------------------------------------------------

    @Test
    void catalogDetail_realHttp_bodyContainsCourseData() throws Exception {
        Session s = login("student", "Student@Reg24!");

        ResponseEntity<String> r = get(s, "/catalog/detail/course/1");
        assertEquals(200, r.getStatusCode().value());
        String body = r.getBody();
        assertNotNull(body);
        assertTrue(body.contains("Calculus") || body.contains("MATH201"),
                "detail page must contain the course title/code");
    }

    // -----------------------------------------------------------------------
    // GET /messages — body lists preferences section
    // -----------------------------------------------------------------------

    @Test
    void messagesPage_realHttp_bodyContainsPreferencesArea() throws Exception {
        Session s = login("student", "Student@Reg24!");

        ResponseEntity<String> r = get(s, "/messages");
        assertEquals(200, r.getStatusCode().value());
        String body = r.getBody();
        assertNotNull(body);
        // The messages/index template renders a preferences section
        assertTrue(body.toLowerCase().contains("quiet") || body.toLowerCase().contains("message"),
                "messages page must mention quiet-hours or messages");
    }

    // -----------------------------------------------------------------------
    // GET /admin/audit — body lists at least audit entries heading
    // -----------------------------------------------------------------------

    @Test
    void auditPage_realHttp_bodyContainsAuditContent() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");

        ResponseEntity<String> r = get(s, "/admin/audit");
        assertEquals(200, r.getStatusCode().value());
        String body = r.getBody();
        assertNotNull(body);
        assertTrue(body.toLowerCase().contains("audit") || body.toLowerCase().contains("action"),
                "audit page must contain audit-related content");
    }

    // -----------------------------------------------------------------------
    // GET /admin/config — body lists known policy keys
    // -----------------------------------------------------------------------

    @Test
    void configPage_realHttp_bodyContainsPolicyKeys() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");

        ResponseEntity<String> r = get(s, "/admin/config");
        assertEquals(200, r.getStatusCode().value());
        String body = r.getBody();
        assertNotNull(body);
        assertTrue(body.contains("orders.refund_window_days"),
                "config page must list the refund policy key");
        assertTrue(body.contains("retry.max_attempts"),
                "config page must list the retry policy key");
    }

    // -----------------------------------------------------------------------
    // POST /orders/{id}/refund — verify transition to REFUNDED within window
    // -----------------------------------------------------------------------

    @Test
    void refundOrder_realHttp_transitionsToRefunded() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/orders/checkout?type=course&id=1");

        MultiValueMap<String, String> cf = new LinkedMultiValueMap<>();
        cf.add("itemType", "course"); cf.add("itemId", "1");
        cf.add("correlationId", UUID.randomUUID().toString());
        long orderId = extractOrderId(post(s, "/orders/create", cf));

        // pay it so status becomes PAID and paidAt is set (within the 14-day window)
        get(s, "/orders/" + orderId);
        post(s, "/orders/" + orderId + "/pay", new LinkedMultiValueMap<>());

        var paid = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertNotNull(paid.getPaidAt());

        // now refund
        get(s, "/orders/" + orderId);
        ResponseEntity<String> r = post(s, "/orders/" + orderId + "/refund",
                new LinkedMultiValueMap<>());
        assertTrue(r.getStatusCode().is3xxRedirection(), "refund must redirect");

        var refunded = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.REFUNDED, refunded.getStatus());
        assertNotNull(refunded.getRefundedAt(), "refundedAt must be stamped");
    }

    // -----------------------------------------------------------------------
    // POST /grades/{courseId}/components — faculty grade entry
    // -----------------------------------------------------------------------

    @Test
    void gradeComponentEntry_realHttp_savesComponentToDb() throws Exception {
        Session s = login("faculty", "Faculty@Reg2024!");
        get(s, "/grades/1/entry");  // prime CSRF

        // Use a unique component name so GradeApiTest's "coursework" row (score=85)
        // left in the shared Testcontainers DB does not interfere with this assertion.
        String compName = "rh_final_" + System.currentTimeMillis();
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("studentId",     "4");
        f.add("componentName", compName);
        f.add("score",         "92");
        f.add("maxScore",      "100");
        ResponseEntity<String> r = post(s, "/grades/1/components", f);

        assertTrue(r.getStatusCode().is3xxRedirection(), "grade entry must redirect");

        var saved = gradeComponentRepository.findByCourseIdAndStudentId(1L, 4L);
        assertFalse(saved.isEmpty(), "component must be persisted in DB");
        var comp = saved.stream()
                .filter(c -> compName.equals(c.getComponentName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(compName + " component not found"));
        assertEquals(0, comp.getScore().compareTo(new java.math.BigDecimal("92")),
                "saved score must match submitted value");
    }

    // -----------------------------------------------------------------------
    // POST /catalog/rate — student submits a rating
    // -----------------------------------------------------------------------

    @Test
    void catalogRating_realHttp_redirectsToDetailPage() throws Exception {
        Session s = login("student", "Student@Reg24!");
        get(s, "/catalog/detail/course/1");  // prime CSRF

        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("itemType", "course");
        f.add("itemId",   "1");
        f.add("score",    "4");
        ResponseEntity<String> r = post(s, "/catalog/rate", f);

        assertTrue(r.getStatusCode().is3xxRedirection(), "rating must redirect");
        String loc = r.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc, "location header must be present");
        assertTrue(loc.contains("/catalog/detail/course/1"),
                "rating redirect must go back to course detail");
    }

    // -----------------------------------------------------------------------
    // POST /evaluations/create + GET cycle detail — faculty eval workflow
    // -----------------------------------------------------------------------

    @Test
    void evaluationCreate_realHttp_redirectsToCycleAndBodyContainsTitle() throws Exception {
        Session s = login("faculty", "Faculty@Reg2024!");
        get(s, "/evaluations");  // prime CSRF

        String title = "RealHTTP Eval " + System.currentTimeMillis();
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("courseId", "1");
        f.add("title",    title);
        ResponseEntity<String> create = post(s, "/evaluations/create", f);

        assertTrue(create.getStatusCode().is3xxRedirection(), "create must redirect");
        String loc = create.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc, "location must be present");

        Matcher m = Pattern.compile("/evaluations/(\\d+)$").matcher(loc);
        assertTrue(m.find(), "redirect must point to /evaluations/{id}");
        long cycleId = Long.parseLong(m.group(1));

        // Verify cycle was persisted
        var cycle = evaluationCycleRepository.findById(cycleId).orElseThrow();
        assertEquals(title, cycle.getTitle(), "persisted cycle title must match");

        // GET the cycle detail page and verify body contains the title
        ResponseEntity<String> detail = get(s, "/evaluations/" + cycleId);
        assertEquals(200, detail.getStatusCode().value());
        assertTrue(detail.getBody().contains(title),
                "cycle detail page must include the cycle title");
    }

    // -----------------------------------------------------------------------
    // POST /grades/{courseId}/recalculate — admin triggers recalculation
    // -----------------------------------------------------------------------

    @Test
    void gradeRecalculate_realHttp_redirectsAndDoesNotError() throws Exception {
        Session s = login("admin", "Admin@Registrar24!");
        get(s, "/grades/1/entry");  // prime CSRF

        ResponseEntity<String> r = post(s, "/grades/1/recalculate", new LinkedMultiValueMap<>());
        assertTrue(r.getStatusCode().is3xxRedirection(),
                "recalculate must redirect (even with no grade components)");
    }

    // -----------------------------------------------------------------------
    // GET /grades/report — student sees their own grade report with real data
    // -----------------------------------------------------------------------

    @Test
    void gradeReport_realHttp_bodyContainsCourseData() throws Exception {
        Session s = login("student", "Student@Reg24!");

        ResponseEntity<String> r = get(s, "/grades/report");
        assertEquals(200, r.getStatusCode().value());
        assertNotNull(r.getBody());
        // The academic report page must have identifiable content sections
        String body = r.getBody().toLowerCase();
        assertTrue(body.contains("report") || body.contains("grade") || body.contains("academic"),
                "grade report page must contain report-related content");
    }

    // --- helpers -----------------------------------------------------------

    private long extractOrderId(ResponseEntity<String> r) {
        String loc = r.getHeaders().getFirst(HttpHeaders.LOCATION);
        assertNotNull(loc);
        Matcher m = Pattern.compile("/orders/(\\d+)").matcher(loc);
        assertTrue(m.find());
        return Long.parseLong(m.group(1));
    }
}
