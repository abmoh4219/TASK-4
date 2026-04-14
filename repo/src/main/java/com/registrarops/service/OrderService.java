package com.registrarops.service;

import com.registrarops.entity.Course;
import com.registrarops.entity.Order;
import com.registrarops.entity.OrderItem;
import com.registrarops.entity.OrderStatus;
import com.registrarops.repository.CourseRepository;
import com.registrarops.repository.OrderItemRepository;
import com.registrarops.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Order lifecycle state machine + 30-minute auto-cancel + 14-day refund policy
 * + correlation-id idempotency.
 *
 * <h3>Allowed transitions</h3>
 * <pre>
 *   CREATED  → PAYING        (student opens checkout)
 *   PAYING   → PAID          (payment confirmed)
 *   CREATED  → CANCELED      (student cancels before paying)
 *   PAYING   → CANCELED      (student cancels OR 30-minute timer expires)
 *   PAID     → REFUNDED      (within 14 days OR exception_status=true)
 * </pre>
 *
 * Any other attempted transition throws {@link OrderStateException}.
 *
 * <h3>Idempotency</h3>
 * createOrder accepts a client-supplied correlationId (UUID). If we already
 * have an order with that id created in the last 10 minutes, we return it
 * unchanged — so a refresh / network retry will not double-charge.
 *
 * <h3>30-minute auto-cancel</h3>
 * {@link #cancelExpiredOrders()} runs every 60 seconds via @Scheduled, finds
 * any order in PAYING that is older than 30 minutes, and cancels it with
 * reason "Payment timeout".
 *
 * <h3>Refund window</h3>
 * Refunds are only allowed within 14 days of paid_at unless
 * {@code exception_status=true}, which an admin can flip manually for a
 * disputed order.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public static final int PAYMENT_TIMEOUT_MINUTES = 30;
    public static final int REFUND_WINDOW_DAYS = 14;
    public static final long IDEMPOTENCY_WINDOW_MINUTES = 10;

    private static final Map<OrderStatus, EnumSet<OrderStatus>> ALLOWED;
    static {
        ALLOWED = new EnumMap<>(OrderStatus.class);
        ALLOWED.put(OrderStatus.CREATED,  EnumSet.of(OrderStatus.PAYING, OrderStatus.CANCELED));
        ALLOWED.put(OrderStatus.PAYING,   EnumSet.of(OrderStatus.PAID,   OrderStatus.CANCELED));
        ALLOWED.put(OrderStatus.PAID,     EnumSet.of(OrderStatus.REFUNDED));
        ALLOWED.put(OrderStatus.CANCELED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.REFUNDED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CourseRepository courseRepository;
    private final AuditService auditService;
    private final MessageService messageService;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CourseRepository courseRepository,
                        AuditService auditService,
                        MessageService messageService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.courseRepository = courseRepository;
        this.auditService = auditService;
        this.messageService = messageService;
    }

    /**
     * Create a brand-new order for a single course item, OR return the existing
     * order if {@code correlationId} was already used in the last 10 minutes.
     */
    @Transactional
    public Order createOrder(Long studentId,
                             String correlationId,
                             String itemType,
                             Long itemId) {
        // Idempotency: same correlationId within 10 min returns the prior order.
        Optional<Order> existing = orderRepository.findByCorrelationId(correlationId);
        if (existing.isPresent()) {
            Order prior = existing.get();
            if (Duration.between(prior.getCreatedAt(), LocalDateTime.now()).toMinutes() <= IDEMPOTENCY_WINDOW_MINUTES) {
                log.info("idempotent createOrder: returning existing order {} for correlationId {}",
                        prior.getId(), correlationId);
                return prior;
            }
            throw new OrderStateException("correlationId " + correlationId + " already used outside idempotency window");
        }

        if (!"course".equalsIgnoreCase(itemType)) {
            throw new IllegalArgumentException("Unsupported item type: " + itemType);
        }
        Course course = courseRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + itemId));

        Order order = new Order();
        order.setCorrelationId(correlationId);
        order.setStudentId(studentId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(course.getPrice());
        order.setExceptionStatus(false);
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        Order saved = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(saved.getId());
        item.setItemType("course");
        item.setItemId(itemId);
        item.setItemName(course.getTitle());
        item.setUnitPrice(course.getPrice());
        item.setQuantity(1);
        item.setSubtotal(course.getPrice());
        orderItemRepository.save(item);

        auditService.log(studentId, null, "ORDER_CREATED", "Order", saved.getId(), null,
                "{\"itemType\":\"" + itemType + "\",\"itemId\":" + itemId
                        + ",\"correlationId\":\"" + correlationId + "\"}", null);

        // Move CREATED → PAYING immediately so the 30-minute clock starts at checkout.
        return startPayment(saved.getId(), studentId);
    }

    /** CREATED → PAYING. */
    @Transactional
    public Order startPayment(Long orderId, Long actorId) {
        Order order = mustFind(orderId);
        transition(order, OrderStatus.PAYING);
        orderRepository.save(order);
        auditService.log(actorId, null, "ORDER_PAYING", "Order", orderId, null, null, null);
        messageService.send(order.getStudentId(), "ORDER",
                "Order awaiting payment",
                "Order #" + orderId + " is awaiting payment. You have 30 minutes to complete it.",
                orderId, "Order");
        return order;
    }

    /** PAYING → PAID. */
    @Transactional
    public Order completePayment(Long orderId, Long actorId) {
        Order order = mustFind(orderId);
        transition(order, OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);
        auditService.log(actorId, null, "ORDER_PAID", "Order", orderId, null, null, null);
        messageService.send(order.getStudentId(), "ORDER",
                "Payment confirmed",
                "Your payment for order #" + orderId + " has been confirmed.",
                orderId, "Order");
        return order;
    }

    /** CREATED or PAYING → CANCELED. */
    @Transactional
    public Order cancelOrder(Long orderId, Long actorId, String reason) {
        Order order = mustFind(orderId);
        transition(order, OrderStatus.CANCELED);
        order.setCanceledAt(LocalDateTime.now());
        order.setCancelReason(reason);
        orderRepository.save(order);
        auditService.log(actorId, null, "ORDER_CANCELED", "Order", orderId, null,
                "{\"reason\":\"" + (reason == null ? "" : reason.replace("\"", "'")) + "\"}", null);
        messageService.send(order.getStudentId(), "ORDER",
                "Order canceled",
                "Order #" + orderId + " has been canceled. " + (reason == null ? "" : reason),
                orderId, "Order");
        return order;
    }

    /**
     * PAID → REFUNDED. Allowed only within 14 days of paid_at, unless the order's
     * exception_status flag is set (admin-only escape hatch for disputed orders).
     */
    @Transactional
    public Order refundOrder(Long orderId, Long actorId) {
        Order order = mustFind(orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new OrderStateException(order.getStatus(), OrderStatus.REFUNDED);
        }
        if (!isRefundAllowed(order)) {
            throw new OrderStateException("Refund window expired (14 days) and exception_status is not set");
        }
        order.setStatus(OrderStatus.REFUNDED);
        order.setRefundedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        auditService.log(actorId, null, "ORDER_REFUNDED", "Order", orderId, null, null, null);
        messageService.send(order.getStudentId(), "ORDER",
                "Order refunded",
                "Order #" + orderId + " has been refunded.",
                orderId, "Order");
        return order;
    }

    /** Pure check used by the controller to decide whether to render the Refund button. */
    public boolean isRefundAllowed(Order order) {
        if (order.getStatus() != OrderStatus.PAID || order.getPaidAt() == null) return false;
        if (Boolean.TRUE.equals(order.getExceptionStatus())) return true;
        return Duration.between(order.getPaidAt(), LocalDateTime.now()).toDays() < REFUND_WINDOW_DAYS;
    }

    /**
     * 30-minute auto-cancel sweeper. Runs every 60 seconds; finds any order
     * stuck in PAYING for longer than 30 minutes and cancels it.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cancelExpiredOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(PAYMENT_TIMEOUT_MINUTES);
        List<Order> expired = orderRepository.findExpiredByStatus(OrderStatus.PAYING, cutoff);
        for (Order o : expired) {
            try {
                cancelOrder(o.getId(), null, "Payment timeout (30 minutes)");
            } catch (Exception e) {
                log.warn("Failed to auto-cancel order {}: {}", o.getId(), e.toString());
            }
        }
        if (!expired.isEmpty()) log.info("auto-canceled {} expired orders", expired.size());
    }

    public List<Order> findByStudent(Long studentId) {
        return orderRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    public Optional<Order> findById(Long orderId) {
        return orderRepository.findById(orderId);
    }

    public List<OrderItem> findItems(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }

    // ---- internals ----------------------------------------------------------

    private Order mustFind(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    /**
     * Apply a state transition or throw OrderStateException. ALL state writes
     * MUST go through this method — the EnumMap above is the single source of
     * truth for legal transitions.
     */
    public void transition(Order order, OrderStatus next) {
        OrderStatus current = order.getStatus();
        if (!ALLOWED.get(current).contains(next)) {
            throw new OrderStateException(current, next);
        }
        order.setStatus(next);
        order.setUpdatedAt(LocalDateTime.now());
    }
}
