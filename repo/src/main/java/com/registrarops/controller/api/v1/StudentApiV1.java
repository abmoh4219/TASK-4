package com.registrarops.controller.api.v1;

import com.registrarops.controller.api.v1.dto.PageQueryDto;
import com.registrarops.entity.Role;
import com.registrarops.entity.StudentGrade;
import com.registrarops.entity.User;
import com.registrarops.repository.StudentGradeRepository;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.GradeAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
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
 * Returns plain JSON (Jackson auto). CSRF is enforced for browser sessions on
 * this endpoint (read-only GETs are unaffected). The integration import/export
 * endpoints under /api/v1/import|export are exempt from CSRF and authenticated
 * via X-API-Key (see {@link com.registrarops.security.ApiKeyAuthFilter}).
 */
@RestController
@RequestMapping("/api/v1/students")
@Validated
public class StudentApiV1 {

    private final UserRepository userRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final GradeAccessPolicy accessPolicy;

    public StudentApiV1(UserRepository userRepository,
                        StudentGradeRepository studentGradeRepository,
                        GradeAccessPolicy accessPolicy) {
        this.userRepository = userRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.accessPolicy = accessPolicy;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public Map<String, Object> list(@Valid @ModelAttribute PageQueryDto query) {
        // Query students at the repository layer so totals only reflect students.
        Page<User> p = userRepository.findByRole(Role.ROLE_STUDENT,
                PageRequest.of(query.getPage(), query.getSize()));
        List<Map<String, Object>> items = p.getContent().stream()
                .map(StudentApiV1::userJson)
                .toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", query.getPage());
        body.put("size", query.getSize());
        body.put("total", p.getTotalElements());
        body.put("totalPages", p.getTotalPages());
        body.put("items", items);
        return body;
    }

    @GetMapping("/{id}/grades")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER','FACULTY','STUDENT')")
    public List<Map<String, Object>> grades(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable @Min(1) Long id) {
        accessPolicy.assertCanReadStudent(principal.getUsername(), id);
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
