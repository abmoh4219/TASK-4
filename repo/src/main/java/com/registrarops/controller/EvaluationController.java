package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 0 placeholder. Phase 5 implements cycles, evidence upload, outlier review.
 */
@Controller
@RequestMapping("/evaluations")
public class EvaluationController {

    @GetMapping
    public String index() {
        return "evaluations/index";
    }
}
