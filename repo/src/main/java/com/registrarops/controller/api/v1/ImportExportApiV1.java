package com.registrarops.controller.api.v1;

import com.registrarops.controller.api.v1.dto.CsvImportMappingDto;
import com.registrarops.entity.RetryJob;
import com.registrarops.entity.User;
import com.registrarops.repository.RetryJobRepository;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.ImportExportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Standardized integration surface for offline/system-to-system import and
 * export. Reuses {@link ImportExportService} — no duplicated business logic.
 *
 * <pre>
 *   POST /api/v1/import/courses       (ADMIN)  multipart + optional mapping
 *   GET  /api/v1/export/courses.csv   (ADMIN)  CSV artifact
 *   GET  /api/v1/retry/jobs           (ADMIN)  retry-job visibility/status
 *   GET  /api/v1/retry/jobs/{id}      (ADMIN)  single retry job detail
 * </pre>
 *
 * Response/error envelopes follow the {@link ApiV1ExceptionHandler} standard.
 */
@RestController
@Validated
public class ImportExportApiV1 {

    private final ImportExportService importExportService;
    private final RetryJobRepository retryJobRepository;
    private final UserRepository userRepository;

    public ImportExportApiV1(ImportExportService importExportService,
                             RetryJobRepository retryJobRepository,
                             UserRepository userRepository) {
        this.importExportService = importExportService;
        this.retryJobRepository = retryJobRepository;
        this.userRepository = userRepository;
    }

    @PostMapping(value = "/api/v1/import/courses",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> importCourses(@AuthenticationPrincipal UserDetails principal,
                                             @RequestParam("file") MultipartFile file,
                                             @Valid @ModelAttribute CsvImportMappingDto mapping) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        User actor = userRepository.findByUsername(principal.getUsername()).orElseThrow();

        ImportExportService.ImportResult result = importExportService.importCoursesCsv(
                file, actor.getId(), actor.getUsername(), mapping.toMapping());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("imported", result.imported);
        body.put("skipped", result.skipped);
        List<Map<String, Object>> errors = result.errors.stream().map(e -> {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("row", e.row());
            em.put("message", e.message());
            return em;
        }).toList();
        body.put("errors", errors);
        return body;
    }

    @GetMapping(value = "/api/v1/export/courses.csv", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ByteArrayResource> exportCourses() {
        byte[] csv = importExportService.exportCoursesCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"courses.csv\"")
                .body(new ByteArrayResource(csv));
    }

    @GetMapping("/api/v1/retry/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> listJobs(@RequestParam(value = "status", required = false) String status) {
        List<RetryJob> jobs;
        if (status == null || status.isBlank()) {
            jobs = retryJobRepository.findAll();
        } else {
            RetryJob.Status s;
            try {
                s = RetryJob.Status.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid status: " + status);
            }
            jobs = retryJobRepository.findByStatus(s);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", jobs.size());
        body.put("items", jobs.stream().map(ImportExportApiV1::jobJson).toList());
        return body;
    }

    @GetMapping("/api/v1/retry/jobs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> getJob(@PathVariable @Min(1) Long id) {
        return retryJobRepository.findById(id)
                .map(ImportExportApiV1::jobJson)
                .orElseThrow(() -> new IllegalArgumentException("retry job not found: " + id));
    }

    private static Map<String, Object> jobJson(RetryJob j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", j.getId());
        m.put("jobType", j.getJobType());
        m.put("status", j.getStatus() == null ? null : j.getStatus().name());
        m.put("attemptCount", j.getAttemptCount());
        m.put("maxAttempts", j.getMaxAttempts());
        m.put("nextRetryAt", j.getNextRetryAt() == null ? null : j.getNextRetryAt().toString());
        m.put("errorMessage", j.getErrorMessage());
        m.put("createdAt", j.getCreatedAt() == null ? null : j.getCreatedAt().toString());
        m.put("updatedAt", j.getUpdatedAt() == null ? null : j.getUpdatedAt().toString());
        return m;
    }
}
