package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 0 placeholder. Phase 5 implements grade entry, GPA reports and rule versioning.
 */
@Controller
@RequestMapping("/grades")
public class GradeController {

    @GetMapping
    public String index() {
        return "grades/index";
    }
}
