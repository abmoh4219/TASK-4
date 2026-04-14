package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 0 placeholder. Phase 6 implements user management, import, audit log, config.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping
    public String index() {
        return "admin/index";
    }

    @GetMapping("/users")
    public String users() {
        return "admin/index";
    }

    @GetMapping("/import")
    public String importPage() {
        return "admin/index";
    }

    @GetMapping("/audit")
    public String audit() {
        return "admin/index";
    }

    @GetMapping("/config")
    public String config() {
        return "admin/index";
    }
}
