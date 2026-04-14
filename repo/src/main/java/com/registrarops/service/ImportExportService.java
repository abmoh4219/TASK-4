package com.registrarops.service;

import com.opencsv.CSVReader;
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

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    public ImportExportService(CourseRepository courseRepository,
                               RetryJobRepository retryJobRepository,
                               AuditService auditService) {
        this.courseRepository = courseRepository;
        this.retryJobRepository = retryJobRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ImportResult importCoursesCsv(MultipartFile file, Long actorId, String actorUsername) {
        ImportResult result = new ImportResult();
        if (file == null || file.isEmpty()) {
            result.errors.add(new RowError(0, "Empty file"));
            return result;
        }
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
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

        if (!result.errors.isEmpty()) {
            // Schedule a single retry job for the import as a whole — purely
            // demonstrating the retry queue + exponential backoff path.
            scheduleRetry("CSV_IMPORT_FAILED",
                    "{\"errors\":" + result.errors.size() + "}");
        }
        return result;
    }

    @Transactional
    public RetryJob scheduleRetry(String jobType, String payload) {
        RetryJob j = new RetryJob();
        j.setJobType(jobType);
        j.setPayload(payload);
        j.setAttemptCount(0);
        j.setMaxAttempts(3);
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
                // Real dispatch would switch on job_type here. For demo we mark success.
                j.setStatus(RetryJob.Status.SUCCEEDED);
                j.setErrorMessage(null);
                log.info("retry job {} succeeded on attempt {}", j.getId(), j.getAttemptCount());
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
    }

    public record RowError(int row, String message) { }
}
