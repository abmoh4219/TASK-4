package com.registrarops.controller.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 0 placeholder. Phase 6 wires this to MessageService for live unread counts.
 * Returns plain text "0" so the HTMX bell renders cleanly on every page.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    @GetMapping(value = "/count", produces = "text/html")
    public String count() {
        return "0";
    }
}
