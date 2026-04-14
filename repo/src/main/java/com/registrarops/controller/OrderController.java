package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 0 placeholder. Phase 4 implements full order lifecycle and 30-min countdown.
 */
@Controller
@RequestMapping("/orders")
public class OrderController {

    @GetMapping
    public String list() {
        return "orders/list";
    }
}
