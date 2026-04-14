package com.registrarops.api;

import com.registrarops.entity.RetryJob;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.RetryJobRepository;
import com.registrarops.service.ImportExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit #8: import field-mapping overrides and real retry-queue processing.
 * These are spring-context tests so they hit a real MySQL via Testcontainers
 * or the external compose database.
 */
class ImportExportApiTest extends AbstractIntegrationTest {

    @Autowired private ImportExportService importExportService;
    @Autowired private CourseRepository courseRepository;
    @Autowired private RetryJobRepository retryJobRepository;

    @Test
    void testImportRespectsFieldMapping() {
        String csv = "sku,name,units,fee,topic\n"
                + "MAPCSV1,Mapped Course 1,3.00,19.99,Mathematics\n"
                + "MAPCSV2,Mapped Course 2,4.00,29.99,Computer Science\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "mapped.csv", "text/csv", csv.getBytes());

        Map<String, String> mapping = Map.of(
                "code", "sku",
                "title", "name",
                "credits", "units",
                "price", "fee",
                "category", "topic");

        ImportExportService.ImportResult result =
                importExportService.importCoursesCsv(file, 1L, "admin", mapping);

        assertEquals(2, result.imported, "both mapped rows should import");
        assertEquals(0, result.skipped);
        assertTrue(courseRepository.findByCode("MAPCSV1").isPresent());
        assertTrue(courseRepository.findByCode("MAPCSV2").isPresent());
    }

    @Test
    void testExportCoursesCsvHasHeaderAndRows() {
        byte[] csv = importExportService.exportCoursesCsv();
        String text = new String(csv);
        assertTrue(text.startsWith("\"code\",\"title\""),
                "export must begin with the canonical header");
        // Seed has a course with code CALC101; verify the row round-trips.
        assertTrue(text.contains("MATH201"), "seeded course should appear in export");
    }

    @Test
    void testDefaultRetryHandlersRegisteredAtStartup() {
        // Audit #3: production job types must have real handlers wired by the
        // bean's @PostConstruct, not only by runtime test registration.
        java.util.Set<String> registered = importExportService.registeredHandlerTypes();
        assertTrue(registered.contains(ImportExportService.JOB_COURSE_IMPORT_ACK),
                "COURSE_IMPORT_ACK handler must be registered at startup");
        assertTrue(registered.contains(ImportExportService.JOB_CATALOG_RECOMPUTE),
                "CATALOG_RECOMPUTE handler must be registered at startup");
    }

    @Test
    void testRetryQueueDispatchMarksSucceededOnRealHandler() {
        // Register a handler that succeeds immediately.
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        importExportService.registerHandler("TEST_OK",
                j -> calls.incrementAndGet());

        RetryJob job = importExportService.scheduleRetry("TEST_OK", "{}");
        job.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        retryJobRepository.saveAndFlush(job);

        importExportService.processRetryQueue();

        RetryJob after = retryJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(RetryJob.Status.SUCCEEDED, after.getStatus());
        assertEquals(1, calls.get(), "handler must actually execute");
        assertEquals(1, after.getAttemptCount());
    }

    @Test
    void testRetryQueueFailThenSuccess() {
        // Handler fails on first attempt, succeeds on second.
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        importExportService.registerHandler("TEST_FAIL_ONCE", j -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("first-attempt failure");
            }
        });

        RetryJob job = importExportService.scheduleRetry("TEST_FAIL_ONCE", "{}");
        job.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        retryJobRepository.saveAndFlush(job);

        importExportService.processRetryQueue();
        RetryJob afterFail = retryJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(RetryJob.Status.PENDING, afterFail.getStatus(),
                "failure must keep job PENDING for retry");
        assertEquals(1, afterFail.getAttemptCount());
        assertNotNull(afterFail.getErrorMessage());

        // Backdate nextRetryAt again so the next tick picks it up.
        afterFail.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        retryJobRepository.saveAndFlush(afterFail);
        importExportService.processRetryQueue();

        RetryJob afterSuccess = retryJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(RetryJob.Status.SUCCEEDED, afterSuccess.getStatus());
        assertEquals(2, afterSuccess.getAttemptCount());
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryQueueAllAttemptsFailMarksFailed() {
        importExportService.registerHandler("TEST_ALWAYS_FAIL", j -> {
            throw new IllegalStateException("permanent failure");
        });

        RetryJob job = importExportService.scheduleRetry("TEST_ALWAYS_FAIL", "{}");
        // maxAttempts defaults to 3; drain all attempts.
        for (int i = 0; i < 3; i++) {
            RetryJob current = retryJobRepository.findById(job.getId()).orElseThrow();
            current.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
            retryJobRepository.saveAndFlush(current);
            importExportService.processRetryQueue();
        }

        RetryJob after = retryJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(RetryJob.Status.FAILED, after.getStatus(),
                "must transition to FAILED after maxAttempts");
        assertEquals(3, after.getAttemptCount());
    }
}
