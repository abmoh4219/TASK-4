package com.registrarops.controller;

import com.registrarops.entity.*;
import com.registrarops.repository.*;
import com.registrarops.service.GradeEngineService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/grades")
public class GradeController {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final GradeRuleRepository gradeRuleRepository;
    private final GradeComponentRepository gradeComponentRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final GradeEngineService gradeEngineService;

    public GradeController(UserRepository userRepository,
                           CourseRepository courseRepository,
                           EnrollmentRepository enrollmentRepository,
                           GradeRuleRepository gradeRuleRepository,
                           GradeComponentRepository gradeComponentRepository,
                           StudentGradeRepository studentGradeRepository,
                           GradeEngineService gradeEngineService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.gradeRuleRepository = gradeRuleRepository;
        this.gradeComponentRepository = gradeComponentRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.gradeEngineService = gradeEngineService;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        if (user.getRole() == Role.ROLE_STUDENT) {
            // Student sees their own grade report.
            return "redirect:/grades/report";
        }
        // Faculty / Admin / Reviewer see the list of all courses to grade.
        model.addAttribute("courses", courseRepository.findByIsActiveTrueOrderByCreatedAtDesc());
        return "grades/index";
    }

    @GetMapping("/report")
    public String report(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        List<StudentGrade> grades = studentGradeRepository.findByStudentIdOrderByCalculatedAtDesc(user.getId());
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal weightedGpa  = BigDecimal.ZERO;
        for (StudentGrade g : grades) {
            totalCredits = totalCredits.add(g.getCredits());
            weightedGpa = weightedGpa.add(g.getGpaPoints().multiply(g.getCredits()));
        }
        BigDecimal cumGpa = totalCredits.signum() > 0
                ? weightedGpa.divide(totalCredits, 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        model.addAttribute("grades", grades);
        model.addAttribute("totalCredits", totalCredits);
        model.addAttribute("cumGpa", cumGpa);
        // Provide per-course title for the report.
        model.addAttribute("courseTitles",
                grades.stream().collect(java.util.stream.Collectors.toMap(
                        StudentGrade::getCourseId,
                        g -> courseRepository.findById(g.getCourseId()).map(Course::getTitle).orElse("?"),
                        (a, b) -> a)));
        return "grades/report";
    }

    @GetMapping("/{courseId}/entry")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String entry(@PathVariable Long courseId, Model model) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        List<Enrollment> enrollments = enrollmentRepository.findByCourseId(courseId);
        List<GradeComponent> components = gradeComponentRepository.findByCourseId(courseId);
        model.addAttribute("course", course);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("components", components);
        model.addAttribute("rule",
                gradeRuleRepository.findFirstByCourseIdAndIsActiveTrueOrderByVersionDesc(courseId).orElse(null));
        return "grades/entry";
    }

    @PostMapping("/{courseId}/components")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String addComponent(@AuthenticationPrincipal UserDetails principal,
                               @PathVariable Long courseId,
                               @RequestParam Long studentId,
                               @RequestParam String componentName,
                               @RequestParam BigDecimal score,
                               @RequestParam(defaultValue = "100.00") BigDecimal maxScore,
                               @RequestParam(defaultValue = "1") Integer attemptNumber,
                               RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        GradeComponent gc = new GradeComponent();
        gc.setCourseId(courseId);
        gc.setStudentId(studentId);
        gc.setComponentName(componentName);
        gc.setScore(score);
        gc.setMaxScore(maxScore);
        gc.setAttemptNumber(attemptNumber);
        gc.setRecordedBy(user.getId());
        gc.setRecordedAt(LocalDateTime.now());
        gradeComponentRepository.save(gc);
        // Recompute this student's grade right away so the entry page shows fresh values.
        gradeRuleRepository.findFirstByCourseIdAndIsActiveTrueOrderByVersionDesc(courseId)
                .ifPresent(rule -> gradeEngineService.calculateGrade(studentId, courseId, rule.getId()));
        redirect.addFlashAttribute("flashSuccess", "Grade component saved.");
        return "redirect:/grades/" + courseId + "/entry";
    }

    @PostMapping("/{courseId}/recalculate")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String recalculate(@PathVariable Long courseId, RedirectAttributes redirect) {
        GradeRule rule = gradeRuleRepository.findFirstByCourseIdAndIsActiveTrueOrderByVersionDesc(courseId)
                .orElseThrow(() -> new IllegalArgumentException("No active rule for course " + courseId));
        int n = gradeEngineService.recalculateAll(courseId, rule.getId());
        redirect.addFlashAttribute("flashSuccess", "Recalculated grades for " + n + " students.");
        return "redirect:/grades/" + courseId + "/entry";
    }
}
