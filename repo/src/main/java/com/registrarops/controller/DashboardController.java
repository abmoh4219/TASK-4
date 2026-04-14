package com.registrarops.controller;

import com.registrarops.entity.EvaluationStatus;
import com.registrarops.entity.OrderStatus;
import com.registrarops.entity.Role;
import com.registrarops.entity.User;
import com.registrarops.repository.*;
import com.registrarops.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

/**
 * Role-aware dashboard controller. The same template renders different cards
 * for each role using sec:authorize blocks.
 */
@Controller
public class DashboardController {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final OrderRepository orderRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final EvaluationCycleRepository cycleRepository;
    private final CourseRepository courseRepository;
    private final AuditLogRepository auditLogRepository;
    private final MessageService messageService;

    public DashboardController(UserRepository userRepository,
                               EnrollmentRepository enrollmentRepository,
                               OrderRepository orderRepository,
                               StudentGradeRepository studentGradeRepository,
                               EvaluationCycleRepository cycleRepository,
                               CourseRepository courseRepository,
                               AuditLogRepository auditLogRepository,
                               MessageService messageService) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.orderRepository = orderRepository;
        this.studentGradeRepository = studentGradeRepository;
        this.cycleRepository = cycleRepository;
        this.courseRepository = courseRepository;
        this.auditLogRepository = auditLogRepository;
        this.messageService = messageService;
    }

    @GetMapping("/")
    public String index(@AuthenticationPrincipal UserDetails principal, Model model) {
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        model.addAttribute("currentUser", user);
        model.addAttribute("unreadCount", messageService.getUnreadCount(user.getId()));

        switch (user.getRole()) {
            case ROLE_STUDENT -> {
                model.addAttribute("enrollments", enrollmentRepository.findByStudentId(user.getId()));
                model.addAttribute("orders", orderRepository.findByStudentIdOrderByCreatedAtDesc(user.getId()));
                model.addAttribute("grades", studentGradeRepository.findByStudentIdOrderByCalculatedAtDesc(user.getId()));
                model.addAttribute("pendingPayingOrders",
                        orderRepository.findExpiredByStatus(OrderStatus.PAYING, LocalDateTime.now().plusDays(1)).size());
            }
            case ROLE_FACULTY -> {
                model.addAttribute("courses", courseRepository.findByIsActiveTrueOrderByCreatedAtDesc());
                model.addAttribute("openCycles", cycleRepository.findByFacultyIdOrderByCreatedAtDesc(user.getId()));
            }
            case ROLE_REVIEWER -> {
                model.addAttribute("submittedCycles", cycleRepository.findByStatus(EvaluationStatus.SUBMITTED));
            }
            case ROLE_ADMIN -> {
                model.addAttribute("userCount", userRepository.count());
                model.addAttribute("courseCount", courseRepository.count());
                model.addAttribute("orderCount", orderRepository.count());
                model.addAttribute("auditCount", auditLogRepository.count());
            }
        }
        return "dashboard/index";
    }
}
