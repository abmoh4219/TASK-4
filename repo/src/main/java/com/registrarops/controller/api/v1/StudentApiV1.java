package com.registrarops.controller.api.v1;

import com.registrarops.entity.Role;
import com.registrarops.entity.StudentGrade;
import com.registrarops.entity.User;
import com.registrarops.repository.StudentGradeRepository;
import com.registrarops.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External integration REST API for student data.
 *
 * Role rules (per the prompt):
 *   GET /api/v1/students            → ADMIN, REVIEWER (paginated)
 *   GET /api/v1/students/{id}/grades → ADMIN, REVIEWER, FACULTY
 *
 * Returns plain JSON (Jackson auto). CSRF is NOT required for GETs and is
 * already excluded for /api/v1/** in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentApiV1 {

    private final UserRepository userRepository;
    private final StudentGradeRepository studentGradeRepository;

    public StudentApiV1(UserRepository userRepository,
                        StudentGradeRepository studentGradeRepository) {
        this.userRepository = userRepository;
        this.studentGradeRepository = studentGradeRepository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public Map<String, Object> list(@RequestParam(value = "page", defaultValue = "0") int page,
                                    @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<User> p = userRepository.findAll(PageRequest.of(page, size));
        List<Map<String, Object>> items = p.getContent().stream()
                .filter(u -> u.getRole() == Role.ROLE_STUDENT)
                .map(StudentApiV1::userJson)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", page);
        body.put("size", size);
        body.put("total", p.getTotalElements());
        body.put("items", items);
        return body;
    }

    @GetMapping("/{id}/grades")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','FACULTY')")
    public List<Map<String, Object>> grades(@PathVariable Long id) {
        List<StudentGrade> grades = studentGradeRepository.findByStudentIdOrderByCalculatedAtDesc(id);
        return grades.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("courseId", g.getCourseId());
            m.put("ruleVersionId", g.getRuleVersionId());
            m.put("weightedScore", g.getWeightedScore());
            m.put("letterGrade", g.getLetterGrade());
            m.put("gpaPoints", g.getGpaPoints());
            m.put("credits", g.getCredits());
            m.put("calculatedAt", g.getCalculatedAt().toString());
            return m;
        }).toList();
    }

    static Map<String, Object> userJson(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("fullName", u.getFullName());
        m.put("role", u.getRole().name());
        m.put("isActive", u.getIsActive());
        return m;
    }
}
