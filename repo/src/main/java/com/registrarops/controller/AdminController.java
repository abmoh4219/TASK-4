package com.registrarops.controller;

import com.registrarops.entity.AuditLog;
import com.registrarops.entity.Role;
import com.registrarops.entity.User;
import com.registrarops.repository.AuditLogRepository;
import com.registrarops.repository.UserRepository;
import com.registrarops.security.PasswordComplexityValidator;
import com.registrarops.service.AuditService;
import com.registrarops.service.ImportExportService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin pages: user management, CSV import, audit log viewer, system config.
 *
 * The audit log viewer is intentionally read-only — there are NO POST or DELETE
 * endpoints anywhere on it. AuditLogRepository similarly exposes no update/delete
 * methods (see its Javadoc).
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final ImportExportService importExportService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final com.registrarops.service.PolicySettingService policySettingService;

    public AdminController(UserRepository userRepository,
                           AuditLogRepository auditLogRepository,
                           ImportExportService importExportService,
                           AuditService auditService,
                           PasswordEncoder passwordEncoder,
                           com.registrarops.service.PolicySettingService policySettingService) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.importExportService = importExportService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.policySettingService = policySettingService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("userCount", userRepository.count());
        model.addAttribute("auditCount", auditLogRepository.count());
        model.addAttribute("retryFailed", importExportService.failedJobs().size());
        return "admin/index";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("roles", Role.values());
        return "admin/users";
    }

    @PostMapping("/users")
    public String createUser(@AuthenticationPrincipal UserDetails principal,
                             @RequestParam String username,
                             @RequestParam String password,
                             @RequestParam Role role,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String fullName,
                             RedirectAttributes redirect) {
        String complaint = PasswordComplexityValidator.validate(password);
        if (complaint != null) {
            redirect.addFlashAttribute("flashError", complaint);
            return "redirect:/admin/users";
        }
        if (userRepository.findByUsername(username).isPresent()) {
            redirect.addFlashAttribute("flashError", "Username already exists");
            return "redirect:/admin/users";
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRole(role);
        u.setEmail(email);
        u.setFullName(fullName);
        u.setIsActive(true);
        userRepository.save(u);

        User actor = userRepository.findByUsername(principal.getUsername()).orElse(null);
        auditService.log(actor == null ? null : actor.getId(),
                principal.getUsername(),
                "USER_CREATED", "User", u.getId(), null,
                "{\"username\":\"" + username + "\",\"role\":\"" + role + "\"}", null);
        redirect.addFlashAttribute("flashSuccess", "User created.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivateUser(@AuthenticationPrincipal UserDetails principal,
                                 @PathVariable Long id,
                                 RedirectAttributes redirect) {
        userRepository.findById(id).ifPresent(u -> {
            u.setIsActive(false);
            userRepository.save(u);
            auditService.log(null, principal.getUsername(), "USER_DEACTIVATED", "User", id,
                    null, null, null);
        });
        redirect.addFlashAttribute("flashSuccess", "User deactivated.");
        return "redirect:/admin/users";
    }

    @GetMapping("/import")
    public String importPage(Model model) {
        model.addAttribute("result", null);
        return "admin/import";
    }

    @PostMapping("/import/csv")
    public String importCsv(@AuthenticationPrincipal UserDetails principal,
                            @RequestParam("file") MultipartFile file,
                            @RequestParam(value = "map_code",     required = false) String mapCode,
                            @RequestParam(value = "map_title",    required = false) String mapTitle,
                            @RequestParam(value = "map_credits",  required = false) String mapCredits,
                            @RequestParam(value = "map_price",    required = false) String mapPrice,
                            @RequestParam(value = "map_category", required = false) String mapCategory,
                            Model model) {
        User actor = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        Map<String, String> mapping = new HashMap<>();
        if (mapCode     != null && !mapCode.isBlank())     mapping.put("code", mapCode);
        if (mapTitle    != null && !mapTitle.isBlank())    mapping.put("title", mapTitle);
        if (mapCredits  != null && !mapCredits.isBlank())  mapping.put("credits", mapCredits);
        if (mapPrice    != null && !mapPrice.isBlank())    mapping.put("price", mapPrice);
        if (mapCategory != null && !mapCategory.isBlank()) mapping.put("category", mapCategory);
        ImportExportService.ImportResult result = importExportService.importCoursesCsv(
                file, actor.getId(), actor.getUsername(), mapping);
        model.addAttribute("result", result);
        return "admin/import";
    }

    @GetMapping("/export/courses.csv")
    public ResponseEntity<ByteArrayResource> exportCourses() {
        byte[] csv = importExportService.exportCoursesCsv();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"courses.csv\"")
                .body(new ByteArrayResource(csv));
    }

    @GetMapping("/audit")
    public String audit(@RequestParam(value = "page", defaultValue = "0") int page,
                        Model model) {
        Page<AuditLog> p = auditLogRepository.findAll(
                PageRequest.of(page, 50, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("logs", p.getContent());
        model.addAttribute("page", page);
        model.addAttribute("hasNext", p.hasNext());
        return "admin/audit";
    }

    @GetMapping("/config")
    public String config(Model model) {
        model.addAttribute("now", LocalDateTime.now());
        model.addAttribute("settings", policySettingService.findAll());
        model.addAttribute("allowedKeys", policySettingService.allowedKeys());
        return "admin/config";
    }

    @PostMapping("/config")
    public String updateConfig(@AuthenticationPrincipal UserDetails principal,
                               @RequestParam("key") String key,
                               @RequestParam("value") String value,
                               RedirectAttributes redirect) {
        User actor = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        try {
            policySettingService.set(key, value, actor.getId(), actor.getUsername());
            redirect.addFlashAttribute("flashSuccess", "Setting '" + key + "' updated.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/admin/config";
    }
}
