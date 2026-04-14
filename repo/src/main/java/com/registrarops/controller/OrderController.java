package com.registrarops.controller;

import com.registrarops.entity.Order;
import com.registrarops.entity.User;
import com.registrarops.repository.UserRepository;
import com.registrarops.service.CatalogService;
import com.registrarops.service.OrderService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneOffset;
import java.util.UUID;

@Controller
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CatalogService catalogService;
    private final UserRepository userRepository;

    public OrderController(OrderService orderService,
                           CatalogService catalogService,
                           UserRepository userRepository) {
        this.orderService = orderService;
        this.catalogService = catalogService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        model.addAttribute("orders", orderService.findByStudent(user.getId()));
        return "orders/list";
    }

    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal UserDetails principal,
                         @PathVariable Long id,
                         Model model) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        Order order = orderService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getStudentId().equals(user.getId())
                && user.getRole().name().equals("ROLE_STUDENT")) {
            throw new AccessDeniedException("Not your order");
        }
        model.addAttribute("order", order);
        model.addAttribute("items", orderService.findItems(id));
        // Epoch ms when the 30-min payment window expires (consumed by app.js countdown).
        long expiresAt = order.getCreatedAt()
                .plusMinutes(OrderService.PAYMENT_TIMEOUT_MINUTES)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli();
        model.addAttribute("expiresAt", expiresAt);
        model.addAttribute("refundAllowed", orderService.isRefundAllowed(order));
        return "orders/detail";
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam("type") String type,
                           @RequestParam("id") Long id,
                           Model model) {
        var course = catalogService.getCourse(id).orElse(null);
        if (course == null) return "redirect:/catalog";
        model.addAttribute("course", course);
        model.addAttribute("itemType", type);
        model.addAttribute("correlationId", UUID.randomUUID().toString());
        return "orders/checkout";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam("itemType") String itemType,
                         @RequestParam("itemId") Long itemId,
                         @RequestParam("correlationId") String correlationId,
                         RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        Order order = orderService.createOrder(user.getId(), correlationId, itemType, itemId);
        redirect.addFlashAttribute("flashSuccess", "Order #" + order.getId() + " created.");
        return "redirect:/orders/" + order.getId();
    }

    @PostMapping("/{id}/pay")
    public String pay(@AuthenticationPrincipal UserDetails principal,
                      @PathVariable Long id,
                      RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        orderService.completePayment(id, user.getId());
        redirect.addFlashAttribute("flashSuccess", "Payment confirmed.");
        return "redirect:/orders/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@AuthenticationPrincipal UserDetails principal,
                         @PathVariable Long id,
                         @RequestParam(value = "reason", required = false) String reason,
                         RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        orderService.cancelOrder(id, user.getId(), reason == null ? "User canceled" : reason);
        redirect.addFlashAttribute("flashSuccess", "Order canceled.");
        return "redirect:/orders/" + id;
    }

    @PostMapping("/{id}/refund")
    public String refund(@AuthenticationPrincipal UserDetails principal,
                         @PathVariable Long id,
                         RedirectAttributes redirect) {
        User user = userRepository.findByUsername(principal.getUsername()).orElseThrow();
        try {
            orderService.refundOrder(id, user.getId());
            redirect.addFlashAttribute("flashSuccess", "Order refunded.");
        } catch (Exception e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/orders/" + id;
    }
}
