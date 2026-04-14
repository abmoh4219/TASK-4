package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 0 placeholder. Phase 3 implements full search, filters and detail pages.
 */
@Controller
@RequestMapping("/catalog")
public class CatalogController {

    @GetMapping
    public String index() {
        return "catalog/index";
    }
}
