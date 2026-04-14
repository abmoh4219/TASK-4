package com.registrarops.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Phase 0 placeholder. Phase 6 implements quiet hours, dedup threading, preferences.
 */
@Controller
@RequestMapping("/messages")
public class MessageController {

    @GetMapping
    public String index() {
        return "messages/index";
    }
}
