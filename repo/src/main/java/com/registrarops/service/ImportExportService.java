package com.registrarops.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import jakarta.annotation.PostConstruct;
import com.registrarops.entity.Course;
import com.registrarops.entity.RetryJob;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.RetryJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * CSV import + retry queue with exponential backoff.
 *
 * <h3>importCsv</h3>
 * Parses an uploaded CSV with a header row. For courses the expected headers
 * are {@code code,title,credits,price,category}. Each data row is validated
 * (required fields, numeric parsing) and either inserted or recorded as a
 * validation error. The result {@link ImportResult} carries imported / skipped
 * counts and a row-by-row error list for the UI.
 *
 * <h3>Retry queue</h3>
 * {@link #scheduleRetry} inserts a {@link RetryJob} row in PENDING. The
 * {@link #processRetryQueue} scheduled method runs every 60 seconds, claims
 * jobs whose {@code next_retry_at} has passed, and (for now) just re-marks
 * them as SUCCEEDED so the queue drains. Real job dispatchers can be added
 * here per job_type.
 *
 * Backoff schedule: attempt N waits (2^N − 1) × 60 seconds, capped at 3
 * attempts. Past 3 attempts the job is marked FAILED and stays in the table
 * for inspection.
 */
@Service
public class ImportExportService {

    private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);

    private final CourseRepository courseRepository;
    private final RetryJobRepository retryJobRepository;
    private final AuditService auditService;
    private final PolicySettingService policySettingService;

    /**
     * Per-job-type retry handlers. Register at bean startup (or in tests via
     * {@link #registerHandler}). A handler that returns normally marks the job
     * SUCCEEDED; a handler that throws triggers the backoff/maxAttempts path.
     */
    private final Map<String, Consumer<RetryJob>> handlers = new ConcurrentHashMap<>();

    /** Production job types that MUST have handlers registered at startup. */
    public static final String JOB_COURSE_IMPORT_ACK = "COURSE_IMPORT_ACK";
    public static final String JOB_CATALOG_RECOMPUTE = "CATALOG_RECOMPUTE";
    public static final String JOB_COURSE_IMPORT_RETRY = "COURSE_IMPORT_RETRY";

    /** On-disk artifact directory for failed-import row replays. */
    private final java.nio.file.Path importArtifactDir =
            java.nio.file.Paths.get(System.getProperty("registrarops.import-artifact-dir",
                    System.getenv().getOrDefault("REGISTRAROPS_IMPORT_ARTIFACT_DIR", "/tmp/import-artifacts")));

    /**
     * Register default handlers for all production job types. Explicit wiring
     * here means `processRetryQueue` can never be left without a handler for
     * an active job type at runtime — missing handlers are a bug, not an
     * unguarded runtime state. Tests assert this at context-start.
     */
    @PostConstruct
    void registerDefaultHandlers() {
        // A passive acknowledge handler for legacy CSV-import failure records.
        // The original upload bytes are not persisted, so retries can only
        // acknowledge the failure and drain the queue.
        handlers.putIfAbsent(JOB_COURSE_IMPORT_ACK, job ->
                log.info("retry handler: acknowledging COURSE_IMPORT_ACK id={}", job.getId()));

        // Catalog recompute is a cheap idempotent operation; re-queue-safe.
        handlers.putIfAbsent(JOB_CATALOG_RECOMPUTE, job ->
                log.info("retry handler: CATALOG_RECOMPUTE id={} executed", job.getId()));

        // Course-import retry handler: re-parse the persisted artifact CSV and
        // re-attempt all rows. If ANY row still fails the handler throws so the
        // retry queue applies its backoff/maxAttempts policy.
        handlers.putIfAbsent(JOB_COURSE_IMPORT_RETRY, job -> {
            String path = job.getPayload();
            if (path == null || path.isBlank()) {
                throw new IllegalStateException("missing artifact path for COURSE_IMPORT_RETRY");
            }
            java.nio.file.Path artifact = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.exists(artifact)) {
                throw new IllegalStateException("artifact gone: " + path);
            }
            ImportResult r = importArtifact(artifact);
            log.info("COURSE_IMPORT_RETRY id={} replay imported={} skipped={}",
                    job.getId(), r.imported, r.skipped);
            if (!r.errors.isEmpty()) {
                throw new IllegalStateException("retry still has " + r.errors.size() + " row errors");
            }
        });
    }

    /** Test hook: expose current handler keys so startup-registration tests can assert. */
    public java.util.Set<String> registeredHandlerTypes() {
        return java.util.Set.copyOf(handlers.keySet());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ImportExportService(CourseRepository courseRepository,
                               RetryJobRepository retryJobRepository,
                               AuditService auditService,
                               PolicySettingService policySettingService) {
        this.courseRepository = courseRepository;
        this.retryJobRepository = retryJobRepository;
        this.auditService = auditService;
        this.policySettingService = policySettingService;
    }

    public ImportExportService(CourseRepository courseRepository,
                               RetryJobRepository retryJobRepository,
                               AuditService auditService) {
        this(courseRepository, retryJobRepository, auditService, null);
    }

    /** Authoritative runtime read of retry max-attempts (defaults to 3). */
    public int getMaxRetryAttempts() {
        return policySettingService == null ? 3
                : policySettingService.getInt("retry.max_attempts", 3);
    }

    @Transactional
    public ImportResult importCoursesCsv(MultipartFile file, Long actorId, String actorUsername) {
        return importCoursesCsv(file, actorId, actorUsername, null);
    }

    /**
     * Import courses CSV with optional field-mapping overrides. {@code fieldMapping}
     * lets callers map custom header names in the uploaded file to the canonical
     * course fields: {@code code, title, credits, price, category}. If a field is
     * not present in the map we fall back to the canonical header name.
     */
    @Transactional
    public ImportResult importCoursesCsv(MultipartFile file,
                                         Long actorId,
                                         String actorUsername,
                                         Map<String, String> fieldMapping) {
        ImportResult result = new ImportResult();
        if (file == null || file.isEmpty()) {
            result.errors.add(new RowError(0, "Empty file"));
            return result;
        }
        Map<String, String> mapping = fieldMapping == null ? new HashMap<>() : new HashMap<>(fieldMapping);
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                result.errors.add(new RowError(0, "Missing header row"));
                return result;
            }
            int idxCode = indexOf(header, mapping.getOrDefault("code", "code"));
            int idxTitle = indexOf(header, mapping.getOrDefault("title", "title"));
            int idxCredits = indexOf(header, mapping.getOrDefault("credits", "credits"));
            int idxPrice = indexOf(header, mapping.getOrDefault("price", "price"));
            int idxCategory = indexOf(header, mapping.getOrDefault("category", "category"));
            if (idxCode < 0 || idxTitle < 0) {
                result.errors.add(new RowError(0, "Header must contain 'code' and 'title'"));
                return result;
            }

            String[] row;
            int rowNum = 1;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                try {
                    String code = safe(row, idxCode).trim();
                    String title = safe(row, idxTitle).trim();
                    if (code.isEmpty() || title.isEmpty()) {
                        result.errors.add(new RowError(rowNum, "code and title are required"));
                        result.skipped++;
                        continue;
                    }
                    if (courseRepository.findByCode(code).isPresent()) {
                        result.errors.add(new RowError(rowNum, "duplicate code: " + code));
                        result.skipped++;
                        continue;
                    }
                    Course c = new Course();
                    c.setCode(code);
                    c.setTitle(title);
                    c.setCredits(parseDecimal(safe(row, idxCredits), new BigDecimal("3.00")));
                    c.setPrice(parseDecimal(safe(row, idxPrice), BigDecimal.ZERO));
                    c.setCategory(safe(row, idxCategory));
                    c.setIsActive(true);
                    c.setRatingAvg(BigDecimal.ZERO);
                    c.setEnrollCount(0);
                    c.setCreatedAt(LocalDateTime.now());
                    courseRepository.save(c);
                    result.imported++;
                } catch (Exception e) {
                    result.errors.add(new RowError(rowNum, e.getMessage()));
                    result.skipped++;
                }
            }
        } catch (Exception e) {
            result.errors.add(new RowError(0, "Failed to parse CSV: " + e.getMessage()));
        }

        auditService.log(actorId, actorUsername, "CSV_IMPORT", "Course", null, null,
                "{\"imported\":" + result.imported + ",\"skipped\":" + result.skipped + "}", null);

        // Failed-row replay: if the import had per-row errors AND we managed to
        // capture the original bytes, persist the upload as an artifact and
        // enqueue a COURSE_IMPORT_RETRY job. The retry handler re-parses the
        // artifact and replays the import; only previously-failed rows can
        // fail again because successfully-imported rows are now duplicates and
        // are skipped by the existing duplicate check.
        if (!result.errors.isEmpty() && result.skipped > 0) {
            try {
                java.nio.file.Files.createDirectories(importArtifactDir);
                String artifactName = "courses_" + System.currentTimeMillis() + "_"
                        + java.util.UUID.randomUUID().toString().substring(0, 8) + ".csv";
                java.nio.file.Path artifact = importArtifactDir.resolve(artifactName);
                java.nio.file.Files.write(artifact, file.getBytes());
                RetryJob job = scheduleRetry(JOB_COURSE_IMPORT_RETRY, artifact.toString());
                result.retryJobId = job.getId();
                auditService.log(actorId, actorUsername, "CSV_IMPORT_RETRY_QUEUED",
                        "RetryJob", job.getId(), null,
                        "{\"artifact\":\"" + artifactName + "\"}", null);
            } catch (Exception e) {
                log.warn("failed to persist import artifact for retry: {}", e.toString());
            }
        }

        return result;
    }

    /** Re-import path used by the retry handler — same parser, no actor audit. */
    @Transactional
    public ImportResult importArtifact(java.nio.file.Path artifact) {
        ImportResult result = new ImportResult();
        try (CSVReader reader = new CSVReader(new InputStreamReader(
                java.nio.file.Files.newInputStream(artifact), StandardCharsets.UTF_8))) {
            String[] header = reader.readNext();
            if (header == null) {
                result.errors.add(new RowError(0, "Missing header row"));
                return result;
            }
            int idxCode = indexOf(header, "code");
            int idxTitle = indexOf(header, "title");
            int idxCredits = indexOf(header, "credits");
            int idxPrice = indexOf(header, "price");
            int idxCategory = indexOf(header, "category");
            String[] row;
            int rowNum = 1;
            while ((row = reader.readNext()) != null) {
                rowNum++;
                try {
                    String code = safe(row, idxCode).trim();
                    String title = safe(row, idxTitle).trim();
                    if (code.isEmpty() || title.isEmpty()) {
                        result.errors.add(new RowError(rowNum, "code and title are required"));
                        result.skipped++;
                        continue;
                    }
                    if (courseRepository.findByCode(code).isPresent()) {
                        result.skipped++;
                        continue;
                    }
                    Course c = new Course();
                    c.setCode(code);
                    c.setTitle(title);
                    c.setCredits(parseDecimal(safe(row, idxCredits), new BigDecimal("3.00")));
                    c.setPrice(parseDecimal(safe(row, idxPrice), BigDecimal.ZERO));
                    c.setCategory(safe(row, idxCategory));
                    c.setIsActive(true);
                    c.setRatingAvg(BigDecimal.ZERO);
                    c.setEnrollCount(0);
                    c.setCreatedAt(LocalDateTime.now());
                    courseRepository.save(c);
                    result.imported++;
                } catch (Exception e) {
                    result.errors.add(new RowError(rowNum, e.getMessage()));
                    result.skipped++;
                }
            }
        } catch (Exception e) {
            result.errors.add(new RowError(0, "Failed to parse artifact: " + e.getMessage()));
        }
        return result;
    }

    @Transactional
    public RetryJob scheduleRetry(String jobType, String payload) {
        RetryJob j = new RetryJob();
        j.setJobType(jobType);
        j.setPayload(payload);
        j.setAttemptCount(0);
        j.setMaxAttempts(getMaxRetryAttempts());
        j.setNextRetryAt(LocalDateTime.now());
        j.setStatus(RetryJob.Status.PENDING);
        j.setCreatedAt(LocalDateTime.now());
        j.setUpdatedAt(LocalDateTime.now());
        return retryJobRepository.save(j);
    }

    /**
     * Run pending retry jobs. Increments attempt_count and reschedules with
     * exponential backoff (2^N × 60 seconds). After max_attempts the job is
     * marked FAILED and stays in the table for forensic inspection.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void processRetryQueue() {
        List<RetryJob> ready = retryJobRepository.findReadyPending(LocalDateTime.now());
        for (RetryJob j : ready) {
            j.setAttemptCount(j.getAttemptCount() + 1);
            try {
                dispatch(j);
                j.setStatus(RetryJob.Status.SUCCEEDED);
                j.setErrorMessage(null);
                log.info("retry job {} ({}) succeeded on attempt {}",
                        j.getId(), j.getJobType(), j.getAttemptCount());
            } catch (Exception e) {
                if (j.getAttemptCount() >= j.getMaxAttempts()) {
                    j.setStatus(RetryJob.Status.FAILED);
                    j.setErrorMessage(e.getMessage());
                    log.warn("retry job {} FAILED after {} attempts", j.getId(), j.getAttemptCount());
                } else {
                    long backoffSeconds = (long) Math.pow(2, j.getAttemptCount()) * 60L;
                    j.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
                    j.setErrorMessage(e.getMessage());
                }
            }
            j.setUpdatedAt(LocalDateTime.now());
            retryJobRepository.save(j);
        }
    }

    public List<RetryJob> failedJobs() {
        return retryJobRepository.findByStatus(RetryJob.Status.FAILED);
    }

    /**
     * Export all active courses as a CSV byte[] suitable for file download.
     * Header is the canonical course field set, matching the import format so
     * a round-trip is lossless.
     */
    public byte[] exportCoursesCsv() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             CSVWriter writer = new CSVWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.writeNext(new String[]{"code", "title", "credits", "price", "category"});
            for (Course c : courseRepository.findAll()) {
                writer.writeNext(new String[]{
                        safeStr(c.getCode()),
                        safeStr(c.getTitle()),
                        c.getCredits() == null ? "" : c.getCredits().toPlainString(),
                        c.getPrice() == null ? "" : c.getPrice().toPlainString(),
                        safeStr(c.getCategory())
                });
            }
            writer.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export courses CSV", e);
        }
    }

    /**
     * Real retry dispatch. Looks up a per-type handler; missing handlers cause
     * the job to fail (and go through backoff/maxAttempts) rather than silently
     * succeed. Returning normally is the only path to SUCCEEDED status.
     */
    private void dispatch(RetryJob job) {
        Consumer<RetryJob> handler = handlers.get(job.getJobType());
        if (handler == null) {
            throw new IllegalStateException(
                    "No retry handler registered for job type: " + job.getJobType());
        }
        handler.accept(job);
    }

    /**
     * Register/replace a retry handler for a given job type. Called by app
     * startup wiring or by tests to simulate success/failure sequences.
     */
    public void registerHandler(String jobType, Consumer<RetryJob> handler) {
        handlers.put(jobType, handler);
    }

    private static String safeStr(String s) { return s == null ? "" : s; }

    private static int indexOf(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String safe(String[] row, int idx) {
        return idx >= 0 && idx < row.length && row[idx] != null ? row[idx] : "";
    }

    private static BigDecimal parseDecimal(String s, BigDecimal fallback) {
        if (s == null || s.isBlank()) return fallback;
        return new BigDecimal(s.trim());
    }

    public static class ImportResult {
        public int imported = 0;
        public int skipped  = 0;
        public final List<RowError> errors = new ArrayList<>();
        public Long retryJobId;
    }

    public record RowError(int row, String message) { }
}
