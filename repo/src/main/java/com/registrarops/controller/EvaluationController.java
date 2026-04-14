package com.registrarops.controller;

import com.registrarops.entity.EvaluationCycle;
import com.registrarops.entity.Role;
import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.EvaluationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final UserRepository userRepository;

    public EvaluationController(EvaluationService evaluationService, UserRepository userRepository) {
        this.evaluationService = evaluationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        if (user.getRole() == Role.ROLE_REVIEWER) {
            model.addAttribute("cycles", evaluationService.listSubmitted());
            model.addAttribute("isReviewer", true);
        } else {
            model.addAttribute("cycles", evaluationService.listForFaculty(user.getId()));
            model.addAttribute("isReviewer", false);
        }
        return "evaluations/index";
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam Long courseId,
                         @RequestParam String title,
                         RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        var c = evaluationService.createCycle(courseId, user.getId(), title);
        redirect.addFlashAttribute("flashSuccess", "Cycle created.");
        return "redirect:/evaluations/" + c.getId();
    }

    @GetMapping("/{cycleId}")
    @PreAuthorize("hasAnyRole('FACULTY','REVIEWER','ADMIN')")
    public String detail(@AuthenticationPrincipal UserDetails principal,
                         @PathVariable Long cycleId, Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        var cycle = evaluationService.find(cycleId);
        assertCycleAccess(user, cycle);
        model.addAttribute("cycle", cycle);
        model.addAttribute("indicators", evaluationService.indicatorsFor(cycleId));
        model.addAttribute("evidence", evaluationService.evidenceFor(cycleId));
        return "evaluations/cycle";
    }

    @PostMapping("/{cycleId}/indicators")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String addIndicator(@AuthenticationPrincipal UserDetails principal,
                               @PathVariable Long cycleId,
                               @RequestParam String indicatorName,
                               @RequestParam BigDecimal weight,
                               @RequestParam BigDecimal score,
                               RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        assertCycleAccess(user, evaluationService.find(cycleId));
        evaluationService.addIndicator(cycleId, indicatorName, weight, score);
        redirect.addFlashAttribute("flashSuccess", "Indicator added.");
        return "redirect:/evaluations/" + cycleId;
    }

    @PostMapping("/{cycleId}/open")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String open(@AuthenticationPrincipal UserDetails principal,
                       @PathVariable Long cycleId,
                       RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        assertCycleAccess(user, evaluationService.find(cycleId));
        evaluationService.openCycle(cycleId, user.getId());
        redirect.addFlashAttribute("flashSuccess", "Cycle opened.");
        return "redirect:/evaluations/" + cycleId;
    }

    @PostMapping("/{cycleId}/submit")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String submit(@AuthenticationPrincipal UserDetails principal,
                         @PathVariable Long cycleId,
                         RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        assertCycleAccess(user, evaluationService.find(cycleId));
        evaluationService.submitCycle(cycleId, user.getId());
        redirect.addFlashAttribute("flashSuccess", "Cycle submitted for review.");
        return "redirect:/evaluations/" + cycleId;
    }

    @PostMapping("/{cycleId}/evidence")
    @PreAuthorize("hasAnyRole('FACULTY','ADMIN')")
    public String uploadEvidence(@AuthenticationPrincipal UserDetails principal,
                                 @PathVariable Long cycleId,
                                 @RequestParam("file") MultipartFile file,
                                 RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        assertCycleAccess(user, evaluationService.find(cycleId));
        try {
            evaluationService.uploadEvidence(cycleId, file, user.getId());
            redirect.addFlashAttribute("flashSuccess", "Evidence uploaded.");
        } catch (Exception e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/evaluations/" + cycleId;
    }

    @GetMapping("/{cycleId}/review")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public String review(@PathVariable Long cycleId, Model model) {
        model.addAttribute("cycle", evaluationService.find(cycleId));
        model.addAttribute("indicators", evaluationService.indicatorsFor(cycleId));
        model.addAttribute("evidence", evaluationService.evidenceFor(cycleId));
        return "evaluations/review";
    }

    @PostMapping("/{cycleId}/approve")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public String approve(@AuthenticationPrincipal UserDetails principal,
                          @PathVariable Long cycleId,
                          @RequestParam(value = "comment", required = false) String comment,
                          RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        evaluationService.reviewerApprove(cycleId, user.getId(), comment);
        redirect.addFlashAttribute("flashSuccess", "Cycle approved and closed.");
        return "redirect:/evaluations/" + cycleId;
    }

    /**
     * Object-level access: a faculty member can only touch their own cycles;
     * reviewers and admins can access any cycle (their role already gates what
     * actions they can call).
     */
    private void assertCycleAccess(User user, EvaluationCycle cycle) {
        if (user.getRole() == Role.ROLE_ADMIN || user.getRole() == Role.ROLE_REVIEWER) return;
        if (user.getRole() == Role.ROLE_FACULTY
                && cycle.getFacultyId() != null
                && cycle.getFacultyId().equals(user.getId())) return;
        throw new AccessDeniedException("Not your evaluation cycle");
    }
}
