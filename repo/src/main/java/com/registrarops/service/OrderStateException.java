package com.registrarops.service;

import com.registrarops.entity.OrderStatus;

/**
 * Thrown by {@link OrderService} when a state transition is not allowed by the
 * order lifecycle state machine (CREATED → PAYING → PAID → REFUNDED, with
 * CREATED/PAYING → CANCELED side branches).
 */
public class OrderStateException extends RuntimeException {
    public OrderStateException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from " + from + " to " + to);
    }
    public OrderStateException(String message) {
        super(message);
    }
}
