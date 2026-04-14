package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 0 placeholder. Phase 2 adds account deletion, profile, export.
 * The /login POST is handled by Spring Security's UsernamePasswordAuthenticationFilter.
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }
}
