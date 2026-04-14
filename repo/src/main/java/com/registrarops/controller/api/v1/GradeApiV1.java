package com.registrarops.controller.api.v1;

import com.registrarops.controller.api.v1.dto.GradeListQueryDto;
import com.registrarops.entity.StudentGrade;
import com.registrarops.repository.GradeRuleRepository;
import com.registrarops.repository.StudentGradeRepository;
import com.registrarops.service.GradeAccessPolicy;
import com.registrarops.service.GradeEngineService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dedicated /api/v1/grades REST surface for external integrations.
 *
 *   GET  /api/v1/grades                        → ADMIN, REVIEWER, FACULTY (paginated)
 *   GET  /api/v1/grades/student/{id}           → ADMIN, REVIEWER, FACULTY
 *   GET  /api/v1/grades/course/{id}            → ADMIN, FACULTY
 *   POST /api/v1/grades/recalculate/{courseId} → ADMIN, FACULTY
 */
@RestController
@RequestMapping("/api/v1/grades")
@Validated
public class GradeApiV1 {

    private final StudentGradeRepository studentGradeRepository;
    private final GradeRuleRepository gradeRuleRepository;
    private final GradeEngineService gradeEngineService;
    private final GradeAccessPolicy accessPolicy;

    public GradeApiV1(StudentGradeRepository studentGradeRepository,
                      GradeRuleRepository gradeRuleRepository,
                      GradeEngineService gradeEngineService,
                      GradeAccessPolicy accessPolicy) {
        this.studentGradeRepository = studentGradeRepository;
        this.gradeRuleRepository = gradeRuleRepository;
        this.gradeEngineService = gradeEngineService;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public Map<String, Object> list(@AuthenticationPrincipal UserDetails principal,
                                    @Valid @ModelAttribute GradeListQueryDto query) {
        // Faculty cannot use this list view — they must fetch via the scoped
        // /student/{id} or /course/{id} endpoints which go through
        // GradeAccessPolicy. Admin/reviewer reach this endpoint directly.
        if (query.getCourseId() != null) {
            accessPolicy.assertCanReadCourse(principal.getUsername(), query.getCourseId());
        }
        List<StudentGrade> all = (query.getCourseId() != null)
                ? studentGradeRepository.findByCourseId(query.getCourseId())
                : studentGradeRepository.findAll();
        int from = Math.min(query.getPage() * query.getSize(), all.size());
        int to = Math.min(from + query.getSize(), all.size());
        List<Map<String, Object>> items = all.subList(from, to).stream().map(GradeApiV1::gradeJson).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", query.getPage());
        body.put("size", query.getSize());
        body.put("total", all.size());
        body.put("items", items);
        return body;
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','FACULTY','STUDENT')")
    public List<Map<String, Object>> byStudent(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable @Min(1) Long studentId) {
        accessPolicy.assertCanReadStudent(principal.getUsername(), studentId);
        return studentGradeRepository.findByStudentIdOrderByCalculatedAtDesc(studentId)
                .stream().map(GradeApiV1::gradeJson).toList();
    }

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','FACULTY')")
    public List<Map<String, Object>> byCourse(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable @Min(1) Long courseId) {
        accessPolicy.assertCanReadCourse(principal.getUsername(), courseId);
        return studentGradeRepository.findByCourseId(courseId)
                .stream().map(GradeApiV1::gradeJson).toList();
    }

    @PostMapping("/recalculate/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN','FACULTY')")
    public ResponseEntity<Map<String, Object>> recalculate(@AuthenticationPrincipal UserDetails principal,
                                                           @PathVariable @Min(1) Long courseId) {
        accessPolicy.assertCanReadCourse(principal.getUsername(), courseId);
        return gradeRuleRepository.findFirstByCourseIdAndIsActiveTrueOrderByVersionDesc(courseId)
                .map(rule -> {
                    int n = gradeEngineService.recalculateAll(courseId, rule.getId());
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("courseId", courseId);
                    body.put("ruleVersionId", rule.getId());
                    body.put("recalculated", n);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(404).build());
    }

    static Map<String, Object> gradeJson(StudentGrade g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("studentId", g.getStudentId());
        m.put("courseId", g.getCourseId());
        m.put("ruleVersionId", g.getRuleVersionId());
        m.put("weightedScore", g.getWeightedScore());
        m.put("letterGrade", g.getLetterGrade());
        m.put("gpaPoints", g.getGpaPoints());
        m.put("credits", g.getCredits());
        m.put("calculatedAt", g.getCalculatedAt() == null ? null : g.getCalculatedAt().toString());
        return m;
    }
}
