package com.registrarops.controller;

import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.AccountDeletionService;
import com.registrarops.service.ExportTokenService;
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
    private final ExportTokenService exportTokenService;

    public AuthController(UserRepository userRepository,
                          AccountDeletionService accountDeletionService,
                          ExportTokenService exportTokenService) {
        this.userRepository = userRepository;
        this.accountDeletionService = accountDeletionService;
        this.exportTokenService = exportTokenService;
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
                                jakarta.servlet.http.HttpServletRequest request,
                                Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        String token = accountDeletionService.exportAndSoftDelete(user.getId());
        // The user is now soft-deleted; their session is no longer valid for any
        // authenticated route. Invalidate it explicitly so the deletion-confirmation
        // page renders cleanly and the token download works post-logout.
        try { request.logout(); } catch (Exception ignored) { }
        if (request.getSession(false) != null) request.getSession().invalidate();
        model.addAttribute("exportUrl", "/account/export/" + token);
        model.addAttribute("ttlDays", 7);
        return "auth/deleted";
    }

    /**
     * Download the local export archive using a self-contained HMAC-signed token.
     * Intentionally NO authentication: the user is soft-deleted immediately at
     * deletion time and login is blocked for the 7-day window, so a session-based
     * gate would make the archive permanently unreachable. The token carries the
     * userId + expiry under a server-side HMAC, so forgery / cross-user / expired
     * tokens are rejected.
     */
    @GetMapping("/account/export/{token}")
    public ResponseEntity<?> downloadExport(@PathVariable String token) {
        Long userId = exportTokenService.verify(token);
        if (userId == null) {
            return ResponseEntity.status(403).build();
        }
        Path file = accountDeletionService.resolveExportFile(userId);
        if (file == null || !file.toFile().exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFileName() + "\"")
                .body(new FileSystemResource(file));
    }
}
