package com.registrarops.controller.api.v1;

import com.registrarops.entity.Role;
import com.registrarops.entity.StudentGrade;
import com.registrarops.entity.User;
import com.registrarops.repository.StudentGradeRepository;
import com.registrarops.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregate report endpoints.
 *   GET /api/v1/reports/gpa-summary → ADMIN only
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportApiV1 {

    private final UserRepository userRepository;
    private final StudentGradeRepository studentGradeRepository;

    public ReportApiV1(UserRepository userRepository,
                       StudentGradeRepository studentGradeRepository) {
        this.userRepository = userRepository;
        this.studentGradeRepository = studentGradeRepository;
    }

    @GetMapping("/gpa-summary")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> gpaSummary() {
        return userRepository.findByRole(Role.ROLE_STUDENT).stream().map(student -> {
            List<StudentGrade> grades = studentGradeRepository.findByStudentIdOrderByCalculatedAtDesc(student.getId());
            BigDecimal totalCredits = BigDecimal.ZERO;
            BigDecimal weighted = BigDecimal.ZERO;
            for (StudentGrade g : grades) {
                totalCredits = totalCredits.add(g.getCredits());
                weighted = weighted.add(g.getGpaPoints().multiply(g.getCredits()));
            }
            BigDecimal gpa = totalCredits.signum() > 0
                    ? weighted.divide(totalCredits, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", student.getId());
            m.put("username", student.getUsername());
            m.put("cumulativeGpa", gpa);
            m.put("totalCredits", totalCredits);
            m.put("courseCount", grades.size());
            return m;
        }).toList();
    }
}
