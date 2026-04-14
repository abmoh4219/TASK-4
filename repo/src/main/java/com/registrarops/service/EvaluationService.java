package com.registrarops.service;

import com.registrarops.entity.*;
import com.registrarops.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluation cycle workflow:
 *
 *   DRAFT  → OPEN       (faculty opens cycle to collect indicator scores)
 *   OPEN   → SUBMITTED  (faculty submits; outliers detected and flagged)
 *   SUBMITTED → REVIEWED → CLOSED  (department reviewer approves)
 *
 * Evidence file uploads are validated against a strict allow-list (PDF / JPG /
 * PNG / DOCX) and a 10 MB hard limit, then SHA-256-hashed and stored under the
 * configured upload directory.
 *
 * Outlier detection (used by the reviewer audit page): for the indicators in a
 * single cycle, compute the mean and standard deviation of their scores, then
 * flag any indicator whose score is more than 2 standard deviations from the
 * mean. The reviewer sees these highlighted in the UI.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    public static final long MAX_EVIDENCE_BYTES = 10L * 1024 * 1024; // 10 MB
    public static final Set<String> ALLOWED_MIME = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/png",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final EvaluationCycleRepository cycleRepository;
    private final EvaluationIndicatorRepository indicatorRepository;
    private final EvidenceAttachmentRepository evidenceRepository;
    private final AuditService auditService;
    private final Path uploadDir;

    public EvaluationService(EvaluationCycleRepository cycleRepository,
                             EvaluationIndicatorRepository indicatorRepository,
                             EvidenceAttachmentRepository evidenceRepository,
                             AuditService auditService,
                             @Value("${registrarops.upload-dir:/app/uploads}") String uploadDir) {
        this.cycleRepository = cycleRepository;
        this.indicatorRepository = indicatorRepository;
        this.evidenceRepository = evidenceRepository;
        this.auditService = auditService;
        this.uploadDir = Paths.get(uploadDir);
    }

    @Transactional
    public EvaluationCycle createCycle(Long courseId, Long facultyId, String title) {
        EvaluationCycle c = new EvaluationCycle();
        c.setCourseId(courseId);
        c.setFacultyId(facultyId);
        c.setTitle(title);
        c.setStatus(EvaluationStatus.DRAFT);
        c.setCreatedAt(LocalDateTime.now());
        EvaluationCycle saved = cycleRepository.save(c);
        auditService.log(facultyId, null, "EVAL_CREATED", "EvaluationCycle", saved.getId(), null,
                "{\"title\":\"" + title.replace("\"", "'") + "\"}", null);
        return saved;
    }

    @Transactional
    public EvaluationIndicator addIndicator(Long cycleId, String name, BigDecimal weight, BigDecimal score) {
        EvaluationIndicator ind = new EvaluationIndicator();
        ind.setCycleId(cycleId);
        ind.setIndicatorName(name);
        ind.setWeight(weight);
        ind.setScore(score);
        ind.setIsOutlier(false);
        return indicatorRepository.save(ind);
    }

    @Transactional
    public void openCycle(Long cycleId, Long actorId) {
        EvaluationCycle c = mustFind(cycleId);
        if (c.getStatus() != EvaluationStatus.DRAFT) {
            throw new IllegalStateException("Cycle must be DRAFT to open, currently " + c.getStatus());
        }
        c.setStatus(EvaluationStatus.OPEN);
        c.setOpenedAt(LocalDateTime.now());
        cycleRepository.save(c);
        auditService.log(actorId, null, "EVAL_OPENED", "EvaluationCycle", cycleId, null, null, null);
    }

    @Transactional
    public void submitCycle(Long cycleId, Long actorId) {
        EvaluationCycle c = mustFind(cycleId);
        if (c.getStatus() != EvaluationStatus.OPEN) {
            throw new IllegalStateException("Cycle must be OPEN to submit, currently " + c.getStatus());
        }
        c.setStatus(EvaluationStatus.SUBMITTED);
        c.setSubmittedAt(LocalDateTime.now());
        cycleRepository.save(c);
        // Compute outliers immediately so the reviewer page can highlight them.
        detectOutliers(cycleId);
        auditService.log(actorId, null, "EVAL_SUBMITTED", "EvaluationCycle", cycleId, null, null, null);
    }

    /**
     * Compute mean / std-dev across the cycle's indicator scores, store on each
     * row, and mark indicators where |score − mean| &gt; 2σ as outliers.
     * Returns the indicators flagged as outliers.
     */
    @Transactional
    public List<EvaluationIndicator> detectOutliers(Long cycleId) {
        List<EvaluationIndicator> indicators = indicatorRepository.findByCycleId(cycleId);
        if (indicators.isEmpty()) return List.of();

        double n = 0, sum = 0;
        for (EvaluationIndicator i : indicators) {
            if (i.getScore() != null) {
                sum += i.getScore().doubleValue();
                n++;
            }
        }
        if (n == 0) return List.of();
        double mean = sum / n;
        double sqdiff = 0;
        for (EvaluationIndicator i : indicators) {
            if (i.getScore() != null) {
                double d = i.getScore().doubleValue() - mean;
                sqdiff += d * d;
            }
        }
        double stdDev = Math.sqrt(sqdiff / n);

        BigDecimal meanBd = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sdBd   = BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP);

        for (EvaluationIndicator i : indicators) {
            i.setMeanScore(meanBd);
            i.setStdDev(sdBd);
            boolean outlier = i.getScore() != null
                    && stdDev > 0
                    && Math.abs(i.getScore().doubleValue() - mean) > 2.0 * stdDev;
            i.setIsOutlier(outlier);
            indicatorRepository.save(i);
        }
        return indicators.stream().filter(EvaluationIndicator::getIsOutlier).toList();
    }

    @Transactional
    public void reviewerApprove(Long cycleId, Long reviewerId, String comment) {
        EvaluationCycle c = mustFind(cycleId);
        if (c.getStatus() != EvaluationStatus.SUBMITTED) {
            throw new IllegalStateException("Cycle must be SUBMITTED to approve, currently " + c.getStatus());
        }
        c.setStatus(EvaluationStatus.CLOSED);
        c.setReviewerComment(comment);
        c.setClosedAt(LocalDateTime.now());
        cycleRepository.save(c);
        auditService.log(reviewerId, null, "EVAL_APPROVED", "EvaluationCycle", cycleId, null,
                "{\"comment\":\"" + (comment == null ? "" : comment.replace("\"", "'")) + "\"}", null);
    }

    /**
     * Validate, hash, and store an evidence attachment for an evaluation cycle.
     * Throws if the file violates the MIME allow-list or 10 MB size cap.
     */
    @Transactional
    public EvidenceAttachment uploadEvidence(Long cycleId, MultipartFile file, Long uploaderId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Empty file");
        }
        if (file.getSize() > MAX_EVIDENCE_BYTES) {
            throw new IllegalArgumentException("File exceeds 10 MB limit");
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME.contains(mime)) {
            throw new IllegalArgumentException("Disallowed MIME type: " + mime);
        }

        Files.createDirectories(uploadDir);
        String safeName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path target = uploadDir.resolve(safeName);
        file.transferTo(target.toFile());

        String hash = sha256(target);

        EvidenceAttachment att = new EvidenceAttachment();
        att.setCycleId(cycleId);
        att.setOriginalFilename(file.getOriginalFilename());
        att.setStoredPath(target.toString());
        att.setMimeType(mime);
        att.setFileSizeBytes(file.getSize());
        att.setSha256Hash(hash);
        att.setUploadedBy(uploaderId);
        att.setUploadedAt(LocalDateTime.now());
        EvidenceAttachment saved = evidenceRepository.save(att);
        auditService.log(uploaderId, null, "EVIDENCE_UPLOADED", "EvaluationCycle", cycleId, null,
                "{\"filename\":\"" + file.getOriginalFilename() + "\",\"sha256\":\"" + hash + "\"}", null);
        return saved;
    }

    public List<EvaluationCycle> listForFaculty(Long facultyId) {
        return cycleRepository.findByFacultyIdOrderByCreatedAtDesc(facultyId);
    }

    public List<EvaluationCycle> listSubmitted() {
        return cycleRepository.findByStatus(EvaluationStatus.SUBMITTED);
    }

    public EvaluationCycle find(Long id) { return mustFind(id); }
    public List<EvaluationIndicator> indicatorsFor(Long cycleId) { return indicatorRepository.findByCycleId(cycleId); }
    public List<EvidenceAttachment>  evidenceFor(Long cycleId)   { return evidenceRepository.findByCycleId(cycleId); }

    private EvaluationCycle mustFind(Long id) {
        return cycleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation cycle not found: " + id));
    }

    private static String sha256(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Failed to compute SHA-256", e);
        }
    }
}
