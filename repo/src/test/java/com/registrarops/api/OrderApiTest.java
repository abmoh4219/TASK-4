package com.registrarops.api;

import com.registrarops.entity.Order;
import com.registrarops.entity.OrderStatus;
import com.registrarops.repository.OrderRepository;
import com.registrarops.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class OrderApiTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testCreateOrderSuccess() throws Exception {
        String corr = UUID.randomUUID().toString();
        mockMvc.perform(post("/orders/create")
                        .with(csrf())
                        .param("itemType", "course")
                        .param("itemId", "2")  // CS301 - paid course
                        .param("correlationId", corr))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/orders/*"));

        Order created = orderRepository.findByCorrelationId(corr).orElseThrow();
        assertEquals(OrderStatus.PAYING, created.getStatus());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testCreateOrderIdempotent() throws Exception {
        String corr = UUID.randomUUID().toString();
        Order first = orderService.createOrder(4L, corr, "course", 1L);
        Order second = orderService.createOrder(4L, corr, "course", 1L);
        assertEquals(first.getId(), second.getId());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testCancelOrderSuccess() throws Exception {
        Order o = orderService.createOrder(4L, UUID.randomUUID().toString(), "course", 1L);
        mockMvc.perform(post("/orders/{id}/cancel", o.getId())
                        .with(csrf())
                        .param("reason", "Changed my mind"))
                .andExpect(status().is3xxRedirection());
        Order reloaded = orderRepository.findById(o.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELED, reloaded.getStatus());
        assertNotNull(reloaded.getCanceledAt());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testRefundWithin14DaysSuccess() throws Exception {
        Order o = orderService.createOrder(4L, UUID.randomUUID().toString(), "course", 1L);
        orderService.completePayment(o.getId(), 4L);
        // Force paidAt within the window (it already is, but make explicit).
        o = orderRepository.findById(o.getId()).orElseThrow();
        o.setPaidAt(LocalDateTime.now().minusDays(2));
        orderRepository.save(o);

        mockMvc.perform(post("/orders/{id}/refund", o.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(OrderStatus.REFUNDED, orderRepository.findById(o.getId()).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testRefundAfter14DaysFails() throws Exception {
        Order o = orderService.createOrder(4L, UUID.randomUUID().toString(), "course", 1L);
        orderService.completePayment(o.getId(), 4L);
        o = orderRepository.findById(o.getId()).orElseThrow();
        o.setPaidAt(LocalDateTime.now().minusDays(20));
        orderRepository.save(o);

        // Refund beyond window should leave status PAID (controller catches and shows flash).
        mockMvc.perform(post("/orders/{id}/refund", o.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(OrderStatus.PAID, orderRepository.findById(o.getId()).orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testStudentCannotSeeOtherOrders() throws Exception {
        // The "student" mock user has username "student" but no DB id.
        // Create an order owned by faculty (id=2) and verify the student cannot view it.
        Order other = orderService.createOrder(2L, UUID.randomUUID().toString(), "course", 1L);
        mockMvc.perform(get("/orders/{id}", other.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCrossUserMutationDenied() {
        // Create an order owned by faculty (id=2). A different actor (student id=4)
        // must be rejected by the service layer even though the controller is
        // bypassed — this proves the object-level check lives in the service.
        Order other = orderService.createOrder(2L, UUID.randomUUID().toString(), "course", 1L);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> orderService.completePayment(other.getId(), 4L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> orderService.cancelOrder(other.getId(), 4L, "not mine"));
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testOrdersListPageRenders() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Orders")));
    }
}
