package com.registrarops.controller.api.v1;

import com.registrarops.entity.Course;
import com.registrarops.entity.StudentGrade;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.StudentGradeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class CourseApiV1 {

    private final CourseRepository courseRepository;
    private final StudentGradeRepository studentGradeRepository;

    public CourseApiV1(CourseRepository courseRepository,
                       StudentGradeRepository studentGradeRepository) {
        this.courseRepository = courseRepository;
        this.studentGradeRepository = studentGradeRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return courseRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(CourseApiV1::courseJson).toList();
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(CourseApiV1::courseJson)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
    }

    @GetMapping("/{id}/grades")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public List<Map<String, Object>> grades(@PathVariable Long id) {
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
