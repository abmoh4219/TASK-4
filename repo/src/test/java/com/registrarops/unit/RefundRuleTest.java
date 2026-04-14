package com.registrarops.unit;

import com.registrarops.entity.Order;
import com.registrarops.entity.OrderStatus;
import com.registrarops.service.OrderService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RefundRuleTest {

    private static OrderService newService() {
        return new OrderService(mock(com.registrarops.repository.OrderRepository.class),
                mock(com.registrarops.repository.OrderItemRepository.class),
                mock(com.registrarops.repository.CourseRepository.class),
                mock(com.registrarops.service.AuditService.class),
                mock(com.registrarops.service.MessageService.class));
    }

    private static Order paid(int daysAgo) {
        Order o = new Order();
        o.setStatus(OrderStatus.PAID);
        o.setPaidAt(LocalDateTime.now().minusDays(daysAgo));
        o.setExceptionStatus(false);
        return o;
    }

    @Test
    void testRefundAllowedBefore14Days() {
        OrderService s = newService();
        assertTrue(s.isRefundAllowed(paid(0)));
        assertTrue(s.isRefundAllowed(paid(7)));
        assertTrue(s.isRefundAllowed(paid(13)));
    }

    @Test
    void testRefundBlockedAfter14Days() {
        OrderService s = newService();
        assertFalse(s.isRefundAllowed(paid(15)));
        assertFalse(s.isRefundAllowed(paid(30)));
    }

    @Test
    void testExceptionStatusAllowsLateRefund() {
        OrderService s = newService();
        Order o = paid(60);
        o.setExceptionStatus(true);
        assertTrue(s.isRefundAllowed(o));
    }

    @Test
    void test14DayBoundaryExact() {
        // exactly 14 days → blocked (rule: "no refunds after 14 days")
        OrderService s = newService();
        assertFalse(s.isRefundAllowed(paid(14)));
    }

    @Test
    void testNonPaidOrdersNotRefundable() {
        OrderService s = newService();
        Order o = new Order();
        o.setStatus(OrderStatus.CREATED);
        assertFalse(s.isRefundAllowed(o));
        o.setStatus(OrderStatus.PAYING);
        assertFalse(s.isRefundAllowed(o));
        o.setStatus(OrderStatus.CANCELED);
        assertFalse(s.isRefundAllowed(o));
        o.setStatus(OrderStatus.REFUNDED);
        assertFalse(s.isRefundAllowed(o));
    }
}
