package com.registrarops.controller.api;

import com.registrarops.repository.UserRepository;
import com.registrarops.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * HTMX endpoints backing the notification bell + drop-down + mark-all-read button.
 *
 * GET /api/notifications/count → small inline HTML for the topbar bell badge
 * GET /api/notifications/list  → Thymeleaf fragment with the unread message list
 * POST /api/notifications/mark-read → mark every unread message for the current user as read
 *
 * The bell polls /count every 30 seconds via the hx-trigger in topbar.html.
 */
@Controller
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final MessageService messageService;
    private final UserRepository userRepository;

    public NotificationApiController(MessageService messageService,
                                     UserRepository userRepository) {
        this.messageService = messageService;
        this.userRepository = userRepository;
    }

    @GetMapping(value = "/count", produces = "text/html")
    @ResponseBody
    public String count(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null) return "0";
        return userRepository.findByUsername(principal.getUsername())
                .map(u -> String.valueOf(messageService.getUnreadCount(u.getId())))
                .orElse("0");
    }

    @GetMapping("/list")
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        userRepository.findByUsername(principal.getUsername()).ifPresent(u -> {
            model.addAttribute("messages", messageService.listForUser(u.getId()));
        });
        return "fragments/notification-badge :: list";
    }

    @PostMapping("/mark-read")
    public String markAllRead(@AuthenticationPrincipal UserDetails principal) {
        userRepository.findByUsername(principal.getUsername())
                .ifPresent(u -> messageService.markAllRead(u.getId()));
        return "redirect:/messages";
    }
}
