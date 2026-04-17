package com.registrarops.unit;

import com.registrarops.entity.Course;
import com.registrarops.entity.RetryJob;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.RetryJobRepository;
import com.registrarops.service.AuditService;
import com.registrarops.service.ImportExportService;
import com.registrarops.service.PolicySettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ImportExportService.
 * All repository / policy dependencies are mocked — no database, no Spring context.
 */
class ImportExportServiceUnitTest {

    private CourseRepository courseRepo;
    private RetryJobRepository retryRepo;
    private AuditService auditService;
    private PolicySettingService policyService;
    private ImportExportService svc;

    @BeforeEach
    void setUp() {
        courseRepo   = mock(CourseRepository.class);
        retryRepo    = mock(RetryJobRepository.class);
        auditService = mock(AuditService.class);
        policyService = mock(PolicySettingService.class);
        when(retryRepo.save(any())).thenAnswer(inv -> {
            RetryJob j = inv.getArgument(0);
            if (j.getId() == null) j.setId(1L);
            return j;
        });
        svc = new ImportExportService(courseRepo, retryRepo, auditService, policyService);
        // @PostConstruct doesn't fire outside Spring — invoke manually via reflection.
        try {
            var m = ImportExportService.class.getDeclaredMethod("registerDefaultHandlers");
            m.setAccessible(true);
            m.invoke(svc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- handler registration -------------------------------------------

    @Test
    void defaultHandlersRegisteredAtStartup() {
        var keys = svc.registeredHandlerTypes();
        assertTrue(keys.contains(ImportExportService.JOB_COURSE_IMPORT_ACK));
        assertTrue(keys.contains(ImportExportService.JOB_CATALOG_RECOMPUTE));
        assertTrue(keys.contains(ImportExportService.JOB_COURSE_IMPORT_RETRY));
    }

    // ---- getMaxRetryAttempts reads from policy ---------------------------

    @Test
    void getMaxRetryAttempts_delegatesToPolicy() {
        when(policyService.getInt("retry.max_attempts", 3)).thenReturn(5);
        assertEquals(5, svc.getMaxRetryAttempts());
    }

    @Test
    void getMaxRetryAttempts_fallsBackToDefault_whenPolicyNull() {
        var svcNoPol = new ImportExportService(courseRepo, retryRepo, auditService);
        assertEquals(3, svcNoPol.getMaxRetryAttempts());
    }

    // ---- scheduleRetry --------------------------------------------------

    @Test
    void scheduleRetry_createsPendingJobWithCorrectFields() {
        when(policyService.getInt("retry.max_attempts", 3)).thenReturn(3);

        RetryJob j = svc.scheduleRetry("SOME_TYPE", "{\"x\":1}");

        assertEquals("SOME_TYPE", j.getJobType());
        assertEquals("{\"x\":1}", j.getPayload());
        assertEquals(RetryJob.Status.PENDING, j.getStatus());
        assertEquals(0, j.getAttemptCount());
        assertEquals(3, j.getMaxAttempts());
        assertNotNull(j.getNextRetryAt());
        assertNotNull(j.getCreatedAt());
        verify(retryRepo).save(any(RetryJob.class));
    }

    // ---- processRetryQueue success path ---------------------------------

    @Test
    void processRetryQueue_successHandler_marksSucceeded() {
        RetryJob j = pendingJob("TEST_OK", 1);
        when(retryRepo.findReadyPending(any())).thenReturn(List.of(j));

        AtomicInteger calls = new AtomicInteger();
        svc.registerHandler("TEST_OK", job -> calls.incrementAndGet());
        svc.processRetryQueue();

        assertEquals(RetryJob.Status.SUCCEEDED, j.getStatus());
        assertEquals(1, j.getAttemptCount());
        assertNull(j.getErrorMessage());
        assertEquals(1, calls.get());
        verify(retryRepo, atLeastOnce()).save(j);
    }

    // ---- processRetryQueue first-attempt failure -------------------------

    @Test
    void processRetryQueue_failingHandler_scheduleBackoff() {
        RetryJob j = pendingJob("FAIL", 3);
        when(retryRepo.findReadyPending(any())).thenReturn(List.of(j));
        svc.registerHandler("FAIL", job -> { throw new RuntimeException("oops"); });

        svc.processRetryQueue();

        assertEquals(RetryJob.Status.PENDING, j.getStatus());
        assertEquals(1, j.getAttemptCount());
        assertNotNull(j.getErrorMessage());
        // Backoff: 2^1 * 60 = 120s; nextRetryAt must be in the future
        assertTrue(j.getNextRetryAt().isAfter(LocalDateTime.now().plusSeconds(30)));
    }

    // ---- processRetryQueue exhausts max attempts → FAILED ---------------

    @Test
    void processRetryQueue_exhaustedAttempts_marksFailed() {
        RetryJob j = pendingJob("FAIL", 3);
        j.setAttemptCount(2); // one attempt left
        when(retryRepo.findReadyPending(any())).thenReturn(List.of(j));
        svc.registerHandler("FAIL", job -> { throw new RuntimeException("permanent"); });

        svc.processRetryQueue();

        assertEquals(RetryJob.Status.FAILED, j.getStatus());
        assertEquals(3, j.getAttemptCount());
        assertNotNull(j.getErrorMessage());
    }

    // ---- processRetryQueue no handler → fails gracefully ----------------

    @Test
    void processRetryQueue_missingHandler_failsJobWithMessage() {
        RetryJob j = pendingJob("NO_HANDLER", 3);
        when(retryRepo.findReadyPending(any())).thenReturn(List.of(j));

        svc.processRetryQueue(); // no handler registered for NO_HANDLER

        // After 3 failed attempts it would be FAILED; here attempt 1 of 3 fails
        assertEquals(RetryJob.Status.PENDING, j.getStatus());
        assertTrue(j.getErrorMessage().contains("No retry handler"));
    }

    // ---- importCsv: empty file ------------------------------------------

    @Test
    void importCsv_emptyFile_returnsErrorWithNoImport() {
        MockMultipartFile f = new MockMultipartFile("file", new byte[0]);
        var r = svc.importCoursesCsv(f, 1L, "admin", null);
        assertEquals(0, r.imported);
        assertFalse(r.errors.isEmpty());
    }

    // ---- importCsv: missing required headers ----------------------------

    @Test
    void importCsv_missingTitleHeader_returnsError() {
        byte[] csv = "code,credits\nX,3\n".getBytes();
        MockMultipartFile f = new MockMultipartFile("file", "x.csv", "text/csv", csv);
        var r = svc.importCoursesCsv(f, 1L, "admin", null);
        assertEquals(0, r.imported);
        assertFalse(r.errors.isEmpty());
        assertTrue(r.errors.get(0).message().contains("title"));
    }

    // ---- importCsv: duplicate code → skipped ----------------------------

    @Test
    void importCsv_duplicateCode_skipsRow() throws Exception {
        when(courseRepo.findByCode("DUP1")).thenReturn(Optional.of(new com.registrarops.entity.Course()));
        byte[] csv = "code,title,credits,price,category\nDUP1,A Course,3,0,Math\n".getBytes();
        MockMultipartFile f = new MockMultipartFile("file", "x.csv", "text/csv", csv);
        var r = svc.importCoursesCsv(f, 1L, "admin", null);
        assertEquals(0, r.imported);
        assertEquals(1, r.skipped);
    }

    // ---- importCsv: happy path ------------------------------------------

    @Test
    void importCsv_validRows_savesCoursesAndAudits() {
        when(courseRepo.findByCode(any())).thenReturn(Optional.empty());
        when(courseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        byte[] csv = "code,title,credits,price,category\nNEW1,New Course,3.00,0.00,Math\n".getBytes();
        MockMultipartFile f = new MockMultipartFile("file", "x.csv", "text/csv", csv);

        var r = svc.importCoursesCsv(f, 1L, "admin", null);

        assertEquals(1, r.imported);
        assertEquals(0, r.skipped);
        verify(courseRepo).save(any());
        verify(auditService).log(eq(1L), eq("admin"), eq("CSV_IMPORT"), any(), any(), any(), any(), any());
    }

    // ---- failedJobs() delegate -----------------------------------------

    @Test
    void failedJobs_delegatesToRepo() {
        when(retryRepo.findByStatus(RetryJob.Status.FAILED)).thenReturn(List.of());
        var result = svc.failedJobs();
        assertNotNull(result);
        verify(retryRepo).findByStatus(RetryJob.Status.FAILED);
    }

    // ---- exportCoursesCsv: header and course rows -----------------------

    @Test
    void exportCoursesCsv_writesHeaderAndCourseRows() {
        Course c = new Course();
        c.setCode("EXP1"); c.setTitle("Export Test Course");
        c.setCredits(new BigDecimal("3.00")); c.setPrice(BigDecimal.ZERO);
        c.setCategory("Science");
        when(courseRepo.findAll()).thenReturn(List.of(c));

        byte[] csv = svc.exportCoursesCsv();
        String content = new String(csv, StandardCharsets.UTF_8);
        assertTrue(content.contains("code"),           "CSV must have 'code' header");
        assertTrue(content.contains("title"),          "CSV must have 'title' header");
        assertTrue(content.contains("EXP1"),           "CSV must contain course code");
        assertTrue(content.contains("Export Test"),    "CSV must contain course title");
        assertTrue(content.contains("Science"),        "CSV must contain category");
    }

    @Test
    void exportCoursesCsv_emptyCatalog_writesHeaderOnly() {
        when(courseRepo.findAll()).thenReturn(List.of());
        byte[] csv = svc.exportCoursesCsv();
        String content = new String(csv, StandardCharsets.UTF_8);
        assertTrue(content.contains("code"),  "header must appear even with no rows");
        assertTrue(content.contains("title"), "header must appear even with no rows");
    }

    // ---- importCsv: field-mapping override ------------------------------

    @Test
    void importCsv_fieldMappingOverride_mapsCustomColumnNames() {
        when(courseRepo.findByCode(any())).thenReturn(Optional.empty());
        when(courseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        byte[] csv = "course_code,course_name,credits,price,category\nMAP1,Mapped Course,3.00,0.00,Math\n"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile f = new MockMultipartFile("file", "x.csv", "text/csv", csv);

        Map<String, String> mapping = new HashMap<>();
        mapping.put("code",  "course_code");
        mapping.put("title", "course_name");

        var r = svc.importCoursesCsv(f, 1L, "admin", mapping);

        assertEquals(1, r.imported,  "custom-header row should import successfully");
        assertEquals(0, r.skipped);
        verify(courseRepo).save(any());
    }

    // ---- importCsv: empty code or title → skip row ----------------------

    @Test
    void importCsv_emptyCode_skipsRowWithError() {
        byte[] csv = "code,title,credits,price,category\n,Empty Code Course,3.00,0.00,Math\n"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile f = new MockMultipartFile("file", "x.csv", "text/csv", csv);
        var r = svc.importCoursesCsv(f, 1L, "admin", null);
        assertEquals(0, r.imported);
        assertEquals(1, r.skipped);
        assertFalse(r.errors.isEmpty(), "empty code must produce an error entry");
    }

    @Test
    void importCsv_emptyTitle_skipsRowWithError() {
        byte[] csv = "code,title,credits,price,category\nEMPTY_TITLE,,3.00,0.00,Math\n"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile f = new MockMultipartFile("file", "x.csv", "text/csv", csv);
        var r = svc.importCoursesCsv(f, 1L, "admin", null);
        assertEquals(0, r.imported);
        assertEquals(1, r.skipped);
    }

    // ---- importArtifact: reads from a file path -------------------------

    @Test
    void importArtifact_validRows_importsCoursesFromFile() throws Exception {
        when(courseRepo.findByCode("ART1")).thenReturn(Optional.empty());
        when(courseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Path temp = Files.createTempFile("artifact_test", ".csv");
        Files.writeString(temp,
                "code,title,credits,price,category\nART1,Artifact Course,3.00,0.00,Math\n",
                StandardCharsets.UTF_8);
        try {
            var r = svc.importArtifact(temp);
            assertEquals(1, r.imported);
            assertEquals(0, r.skipped);
            assertTrue(r.errors.isEmpty());
            verify(courseRepo).save(any());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void importArtifact_duplicateCode_skipsWithoutError() throws Exception {
        when(courseRepo.findByCode("DUP_ART")).thenReturn(Optional.of(new Course()));

        Path temp = Files.createTempFile("artifact_dup", ".csv");
        Files.writeString(temp,
                "code,title,credits,price,category\nDUP_ART,Dup Course,3.00,0.00,Math\n",
                StandardCharsets.UTF_8);
        try {
            var r = svc.importArtifact(temp);
            assertEquals(0, r.imported);
            assertEquals(1, r.skipped);
            assertTrue(r.errors.isEmpty(), "artifact import silently skips duplicates");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ---- processRetryQueue: multiple jobs processed in one pass ---------

    @Test
    void processRetryQueue_multipleJobs_processesAll() {
        RetryJob j1 = pendingJob("JOB_MULTI_A", 3);
        j1.setId(10L);
        RetryJob j2 = pendingJob("JOB_MULTI_B", 3);
        j2.setId(11L);
        when(retryRepo.findReadyPending(any())).thenReturn(List.of(j1, j2));

        AtomicInteger aCalls = new AtomicInteger();
        AtomicInteger bCalls = new AtomicInteger();
        svc.registerHandler("JOB_MULTI_A", job -> aCalls.incrementAndGet());
        svc.registerHandler("JOB_MULTI_B", job -> bCalls.incrementAndGet());

        svc.processRetryQueue();

        assertEquals(RetryJob.Status.SUCCEEDED, j1.getStatus());
        assertEquals(RetryJob.Status.SUCCEEDED, j2.getStatus());
        assertEquals(1, aCalls.get(), "JOB_MULTI_A handler called once");
        assertEquals(1, bCalls.get(), "JOB_MULTI_B handler called once");
        verify(retryRepo, times(2)).save(any(RetryJob.class));
    }

    // ---- importCsv: multiple valid rows ---------------------------------

    @Test
    void importCsv_multipleValidRows_importsAll() {
        when(courseRepo.findByCode(any())).thenReturn(Optional.empty());
        when(courseRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        byte[] csv = ("code,title,credits,price,category\n"
                + "MULTI1,Course One,3.00,0.00,Math\n"
                + "MULTI2,Course Two,2.00,9.99,Science\n"
                + "MULTI3,Course Three,4.00,19.99,Art\n").getBytes(StandardCharsets.UTF_8);
        MockMultipartFile f = new MockMultipartFile("file", "x.csv", "text/csv", csv);

        var r = svc.importCoursesCsv(f, 1L, "admin", null);

        assertEquals(3, r.imported);
        assertEquals(0, r.skipped);
        assertTrue(r.errors.isEmpty());
        verify(courseRepo, times(3)).save(any());
    }

    // ---- helpers --------------------------------------------------------

    private RetryJob pendingJob(String type, int maxAttempts) {
        RetryJob j = new RetryJob();
        j.setId(1L); j.setJobType(type); j.setPayload("{}");
        j.setAttemptCount(0); j.setMaxAttempts(maxAttempts);
        j.setStatus(RetryJob.Status.PENDING);
        j.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        j.setCreatedAt(LocalDateTime.now()); j.setUpdatedAt(LocalDateTime.now());
        return j;
    }
}
