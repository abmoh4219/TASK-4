package com.registrarops.controller;

import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.MessageService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;
    private final UserRepository userRepository;

    public MessageController(MessageService messageService, UserRepository userRepository) {
        this.messageService = messageService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        model.addAttribute("messages", messageService.listForUser(user.getId()));
        model.addAttribute("preferences", messageService.getPreferences(user.getId()));
        return "messages/index";
    }

    @PostMapping("/mark-read")
    public String markAllRead(@AuthenticationPrincipal UserDetails principal,
                              RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        messageService.markAllRead(user.getId());
        redirect.addFlashAttribute("flashSuccess", "All messages marked as read.");
        return "redirect:/messages";
    }

    @PostMapping("/preferences/mute")
    public String mute(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam String category,
                       RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        messageService.muteCategory(user.getId(), category);
        redirect.addFlashAttribute("flashSuccess", "Category muted.");
        return "redirect:/messages";
    }

    @PostMapping("/preferences/quiet-hours")
    public String quietHours(@AuthenticationPrincipal UserDetails principal,
                             @RequestParam int startHour,
                             @RequestParam int endHour,
                             RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        messageService.updateQuietHours(user.getId(), startHour, endHour);
        redirect.addFlashAttribute("flashSuccess", "Quiet hours updated.");
        return "redirect:/messages";
    }
}
