package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Phase 0 placeholder. Phase 7 will populate role-specific dashboard data.
 */
@Controller
public class DashboardController {

    @GetMapping("/")
    public String index() {
        return "dashboard/index";
    }
}
