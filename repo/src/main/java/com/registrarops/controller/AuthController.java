package com.registrarops.controller;

import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.AccountDeletionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Phase 2 AuthController: login GET + profile + account deletion + export download.
 *
 * The /login POST is handled by Spring Security's UsernamePasswordAuthenticationFilter
 * (configured in SecurityConfig) — this controller does NOT have a POST /login.
 */
@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final AccountDeletionService accountDeletionService;

    public AuthController(UserRepository userRepository,
                          AccountDeletionService accountDeletionService) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/profile")
    public String profile(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        model.addAttribute("user", user);
        return "auth/profile";
    }

    @GetMapping("/account/delete")
    public String confirmDelete() {
        return "auth/delete";
    }

    @PostMapping("/account/delete")
    public String performDelete(@AuthenticationPrincipal UserDetails principal,
                                RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        String token = accountDeletionService.exportAndSoftDelete(user.getId());
        redirect.addFlashAttribute("flashSuccess",
                "Account deleted. Download your data archive within 7 days using the link in your email queue.");
        return "redirect:/account/export/" + token;
    }

    /**
     * Download the local export file. Token is embedded in the filename
     * (user_{id}_{token}.json) so we look it up via the user record's stored path.
     */
    @GetMapping("/account/export/{token}")
    public ResponseEntity<?> downloadExport(@PathVariable String token) {
        return userRepository.findAll().stream()
                .filter(u -> u.getExportFilePath() != null && u.getExportFilePath().contains(token))
                .findFirst()
                .map(u -> {
                    Path file = Paths.get(u.getExportFilePath());
                    if (!file.toFile().exists()) {
                        return ResponseEntity.notFound().build();
                    }
                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"" + file.getFileName() + "\"")
                            .body((Object) new FileSystemResource(file));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
