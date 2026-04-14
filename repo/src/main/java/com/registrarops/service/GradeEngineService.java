package com.registrarops.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.registrarops.entity.*;
import com.registrarops.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Grade calculation engine.
 *
 * <h3>Weighted score</h3>
 * Each {@link GradeRule} stores a JSON object of category weights, e.g.
 * {@code {"coursework":30,"midterm":20,"final":50}} (sums to 100). For each
 * grading component recorded for a student, we look up its weight by
 * component_name and compute:
 *
 * <pre>weightedScore = Σ ( componentScore / componentMaxScore ) × weight</pre>
 *
 * <h3>Retake policy</h3>
 * - HIGHEST_SCORE: when a student has multiple attempts at the same component,
 *   take the maximum score.
 * - LATEST_SCORE: take the most recent attempt (largest attempt_number).
 *
 * <h3>GPA scale (configurable in Phase 6, hard-coded here)</h3>
 * 90–100→4.0, 85–89→3.7, 80–84→3.3, 75–79→3.0, 70–74→2.7, 60–69→2.0, &lt;60→0.0
 *
 * <h3>Rule versioning &amp; backtracking</h3>
 * StudentGrade stores the {@code rule_version_id} that produced it. When an
 * admin changes the active rule for a course, {@link #recalculateAll} produces
 * a new StudentGrade row referencing the new rule version — old rows are kept
 * intact so the historic calculation is reproducible.
 */
@Service
public class GradeEngineService {

    private static final Logger log = LoggerFactory.getLogger(GradeEngineService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GradeRuleRepository gradeRuleRepository;
    private final GradeComponentRepository gradeComponentRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final AuditService auditService;

    public GradeEngineService(GradeRuleRepository gradeRuleRepository,
                              GradeComponentRepository gradeComponentRepository,
                              StudentGradeRepository studentGradeRepository,
                              EnrollmentRepository enrollmentRepository,
                              CourseRepository courseRepository,
                              AuditService auditService) {
        this.gradeRuleRepository = gradeRuleRepository;
        this.gradeComponentRepository = gradeComponentRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.auditService = auditService;
    }

    /**
     * Calculate (and persist) a single student's grade for one course using a
     * specific rule version. Returns the new StudentGrade row.
     */
    @Transactional
    public StudentGrade calculateGrade(Long studentId, Long courseId, Long ruleVersionId) {
        GradeRule rule = gradeRuleRepository.findById(ruleVersionId)
                .orElseThrow(() -> new IllegalArgumentException("Grade rule not found: " + ruleVersionId));
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        Map<String, BigDecimal> weights = parseWeights(rule.getWeightsJson());
        List<GradeComponent> all = gradeComponentRepository.findByCourseIdAndStudentId(courseId, studentId);

        // Apply retake policy: collapse multiple attempts of the same component_name
        // down to a single chosen score using either the max or the most recent.
        Map<String, GradeComponent> chosen = new LinkedHashMap<>();
        for (GradeComponent gc : all) {
            chosen.merge(gc.getComponentName(), gc, (existing, candidate) -> {
                if (rule.getRetakePolicy() == RetakePolicy.HIGHEST_SCORE) {
                    return candidate.getScore().compareTo(existing.getScore()) > 0 ? candidate : existing;
                }
                // LATEST_SCORE
                return candidate.getAttemptNumber() > existing.getAttemptNumber() ? candidate : existing;
            });
        }

        BigDecimal weighted = BigDecimal.ZERO;
        for (Map.Entry<String, GradeComponent> entry : chosen.entrySet()) {
            BigDecimal weight = weights.get(entry.getKey());
            if (weight == null) continue;
            GradeComponent gc = entry.getValue();
            // (score / max) * weight
            BigDecimal pct = gc.getScore().divide(gc.getMaxScore(), 6, RoundingMode.HALF_UP);
            weighted = weighted.add(pct.multiply(weight));
        }
        weighted = weighted.setScale(2, RoundingMode.HALF_UP);

        BigDecimal gpa = convertToGpa(weighted);
        String letter = letterGrade(weighted);

        StudentGrade sg = new StudentGrade();
        sg.setStudentId(studentId);
        sg.setCourseId(courseId);
        sg.setRuleVersionId(ruleVersionId);
        sg.setWeightedScore(weighted);
        sg.setGpaPoints(gpa);
        sg.setLetterGrade(letter);
        sg.setCredits(course.getCredits());
        sg.setCalculatedAt(LocalDateTime.now());
        StudentGrade saved = studentGradeRepository.save(sg);

        auditService.logSystem("GRADE_CALCULATED", "StudentGrade", saved.getId(),
                "{\"studentId\":" + studentId + ",\"courseId\":" + courseId
                        + ",\"weightedScore\":" + weighted + ",\"gpa\":" + gpa + "}");
        return saved;
    }

    /**
     * Re-run grade calculation for every student enrolled in a course using the
     * supplied rule version. Old StudentGrade rows referencing the old rule are
     * kept intact (audit trail / backtracking).
     */
    @Transactional
    public int recalculateAll(Long courseId, Long newRuleVersionId) {
        List<Enrollment> enrollments = enrollmentRepository.findByCourseId(courseId);
        int n = 0;
        for (Enrollment e : enrollments) {
            try {
                calculateGrade(e.getStudentId(), courseId, newRuleVersionId);
                n++;
            } catch (Exception ex) {
                log.warn("recalculate failed for student {} course {}: {}",
                        e.getStudentId(), courseId, ex.toString());
            }
        }
        return n;
    }

    /** Convert a 0–100 weighted score to a 4.0-scale GPA point value. */
    public static BigDecimal convertToGpa(BigDecimal weightedScore) {
        if (weightedScore == null) return BigDecimal.ZERO;
        double s = weightedScore.doubleValue();
        if (s >= 90) return new BigDecimal("4.00");
        if (s >= 85) return new BigDecimal("3.70");
        if (s >= 80) return new BigDecimal("3.30");
        if (s >= 75) return new BigDecimal("3.00");
        if (s >= 70) return new BigDecimal("2.70");
        if (s >= 60) return new BigDecimal("2.00");
        return BigDecimal.ZERO;
    }

    public static String letterGrade(BigDecimal weightedScore) {
        if (weightedScore == null) return "F";
        double s = weightedScore.doubleValue();
        if (s >= 90) return "A";
        if (s >= 85) return "A-";
        if (s >= 80) return "B+";
        if (s >= 75) return "B";
        if (s >= 70) return "B-";
        if (s >= 60) return "C";
        return "F";
    }

    /** Parse a weights JSON string into {component → weight}. */
    public static Map<String, BigDecimal> parseWeights(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Number> raw = MAPPER.readValue(json, new TypeReference<>() { });
            Map<String, BigDecimal> out = new LinkedHashMap<>();
            raw.forEach((k, v) -> out.put(k, new BigDecimal(v.toString())));
            return out;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid weights JSON: " + json, e);
        }
    }
}
