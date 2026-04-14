package com.registrarops.unit;

import com.registrarops.entity.Course;
import com.registrarops.entity.Order;
import com.registrarops.entity.OrderStatus;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.OrderItemRepository;
import com.registrarops.repository.OrderRepository;
import com.registrarops.service.AuditService;
import com.registrarops.service.MessageService;
import com.registrarops.service.OrderService;
import com.registrarops.service.OrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderStateMachineTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private CourseRepository courseRepository;
    private AuditService auditService;
    private MessageService messageService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        courseRepository = mock(CourseRepository.class);
        auditService = mock(AuditService.class);
        messageService = mock(MessageService.class);
        orderService = new OrderService(orderRepository, orderItemRepository, courseRepository,
                auditService, messageService);
        // Saves are pass-through.
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Order order(OrderStatus status) {
        Order o = new Order();
        o.setId(1L);
        o.setStudentId(42L);
        o.setStatus(status);
        o.setTotalAmount(BigDecimal.ZERO);
        o.setCorrelationId(UUID.randomUUID().toString());
        o.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        o.setUpdatedAt(LocalDateTime.now());
        return o;
    }

    @Test
    void testCreatedToPayingValid() {
        Order o = order(OrderStatus.CREATED);
        orderService.transition(o, OrderStatus.PAYING);
        assertEquals(OrderStatus.PAYING, o.getStatus());
    }

    @Test
    void testPayingToPaidValid() {
        Order o = order(OrderStatus.PAYING);
        orderService.transition(o, OrderStatus.PAID);
        assertEquals(OrderStatus.PAID, o.getStatus());
    }

    @Test
    void testPaidToRefundedValid() {
        Order o = order(OrderStatus.PAID);
        orderService.transition(o, OrderStatus.REFUNDED);
        assertEquals(OrderStatus.REFUNDED, o.getStatus());
    }

    @Test
    void testInvalidTransitionThrows() {
        // CREATED → PAID (skipping PAYING) is not allowed.
        Order o = order(OrderStatus.CREATED);
        assertThrows(OrderStateException.class, () -> orderService.transition(o, OrderStatus.PAID));

        // CANCELED is terminal.
        Order canceled = order(OrderStatus.CANCELED);
        assertThrows(OrderStateException.class, () -> orderService.transition(canceled, OrderStatus.PAID));

        // REFUNDED is terminal.
        Order refunded = order(OrderStatus.REFUNDED);
        assertThrows(OrderStateException.class, () -> orderService.transition(refunded, OrderStatus.PAID));
    }

    @Test
    void testCreatedAndPayingCanCancel() {
        orderService.transition(order(OrderStatus.CREATED), OrderStatus.CANCELED);
        orderService.transition(order(OrderStatus.PAYING), OrderStatus.CANCELED);
    }

    @Test
    void testIdempotentOrderCreation() {
        // Same correlationId returned within 10-min window → returns existing, no second insert.
        Order existing = order(OrderStatus.PAYING);
        existing.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(orderRepository.findByCorrelationId("CORR-1")).thenReturn(Optional.of(existing));

        Order returned = orderService.createOrder(42L, "CORR-1", "course", 1L);

        assertSame(existing, returned);
        verify(orderRepository, never()).save(argThat(o -> o.getId() == null));
    }

    @Test
    void testIdempotencyExpiredThrows() {
        Order ancient = order(OrderStatus.PAYING);
        ancient.setCreatedAt(LocalDateTime.now().minusHours(2));
        when(orderRepository.findByCorrelationId("CORR-2")).thenReturn(Optional.of(ancient));

        assertThrows(OrderStateException.class,
                () -> orderService.createOrder(42L, "CORR-2", "course", 1L));
    }

    @Test
    void testAutoCancel30MinExpiry() {
        Order expired = order(OrderStatus.PAYING);
        expired.setCreatedAt(LocalDateTime.now().minusMinutes(31));
        when(orderRepository.findExpiredByStatus(eq(OrderStatus.PAYING), any()))
                .thenReturn(List.of(expired));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(expired));

        orderService.cancelExpiredOrders();

        assertEquals(OrderStatus.CANCELED, expired.getStatus());
        assertNotNull(expired.getCanceledAt());
    }
}
