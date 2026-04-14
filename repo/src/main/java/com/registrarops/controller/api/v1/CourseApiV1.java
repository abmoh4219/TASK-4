package com.registrarops.controller.api.v1;

import com.registrarops.entity.Course;
import com.registrarops.entity.StudentGrade;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.StudentGradeRepository;
import com.registrarops.service.GradeAccessPolicy;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External integration REST API for courses.
 *   GET /api/v1/courses              → all authenticated users
 *   GET /api/v1/courses/{id}/grades  → FACULTY, ADMIN
 */
@RestController
@RequestMapping("/api/v1/courses")
@Validated
public class CourseApiV1 {

    private final CourseRepository courseRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final GradeAccessPolicy accessPolicy;

    public CourseApiV1(CourseRepository courseRepository,
                       StudentGradeRepository studentGradeRepository,
                       GradeAccessPolicy accessPolicy) {
        this.courseRepository = courseRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','FACULTY','REVIEWER','ADMIN')")
    public List<Map<String, Object>> list() {
        return courseRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(CourseApiV1::courseJson).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','FACULTY','REVIEWER','ADMIN')")
    public Map<String, Object> get(@PathVariable @Min(1) Long id) {
        return courseRepository.findById(id)
                .map(CourseApiV1::courseJson)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
    }

    @GetMapping("/{id}/grades")
    @PreAuthorize("hasAnyRole('FACULTY','REVIEWER','ADMIN')")
    public List<Map<String, Object>> grades(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable @Min(1) Long id) {
        // Object-level scoping — faculty may only read courses where they
        // have recorded components; admin/reviewer are unrestricted.
        accessPolicy.assertCanReadCourse(principal.getUsername(), id);
        List<StudentGrade> grades = studentGradeRepository.findByCourseId(id);
        return grades.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", g.getStudentId());
            m.put("weightedScore", g.getWeightedScore());
            m.put("letterGrade", g.getLetterGrade());
            m.put("gpaPoints", g.getGpaPoints());
            return m;
        }).toList();
    }

    static Map<String, Object> courseJson(Course c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("code", c.getCode());
        m.put("title", c.getTitle());
        m.put("category", c.getCategory());
        m.put("credits", c.getCredits());
        m.put("price", c.getPrice());
        m.put("ratingAvg", c.getRatingAvg());
        m.put("isActive", c.getIsActive());
        return m;
    }
}
