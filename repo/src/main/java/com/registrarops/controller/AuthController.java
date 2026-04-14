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

    @PostMapping("/profile/device-binding")
    public String updateDeviceBinding(@AuthenticationPrincipal UserDetails principal,
                                      @org.springframework.web.bind.annotation.RequestParam(value = "enabled", defaultValue = "false") boolean enabled,
                                      RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        user.setDeviceBindingEnabled(enabled);
        userRepository.save(user);
        redirect.addFlashAttribute("flashSuccess",
                enabled ? "Device binding enabled — you'll see an in-app notice on new sign-in devices."
                        : "Device binding disabled.");
        return "redirect:/profile";
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
                "Account deleted. Your data archive is available for in-app download for 7 days using the link below.");
        return "redirect:/account/export/" + token;
    }

    /**
     * Download the local export file. The token is bound to the authenticated
     * principal: we load ONLY the current user's export-file record and compare
     * the token from the filename with exact equality. No cross-user scan.
     */
    @GetMapping("/account/export/{token}")
    public ResponseEntity<?> downloadExport(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable String token) {
        if (principal == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByUsername(principal.getUsername()).orElse(null);
        if (user == null || user.getExportFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path file = Paths.get(user.getExportFilePath());
        String expectedToken = extractToken(file.getFileName().toString(), user.getId());
        if (expectedToken == null || !expectedToken.equals(token)) {
            return ResponseEntity.status(403).build();
        }
        if (!file.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName() + "\"")
                .body(new FileSystemResource(file));
    }

    /** Parse "user_{id}_{token}.json" → {token}, returning null on mismatch. */
    private static String extractToken(String filename, Long userId) {
        String prefix = "user_" + userId + "_";
        if (filename == null || !filename.startsWith(prefix) || !filename.endsWith(".json")) return null;
        return filename.substring(prefix.length(), filename.length() - ".json".length());
    }
}
