package com.registrarops.repository;

import com.registrarops.entity.Order;
import com.registrarops.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCorrelationId(String correlationId);

    List<Order> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :before")
    List<Order> findExpiredByStatus(@Param("status") OrderStatus status,
                                    @Param("before") LocalDateTime before);

    /** Orders in PAYING whose createdAt falls in the reminder window [windowStart, windowEnd]. */
    @Query("SELECT o FROM Order o WHERE o.status = :status " +
           "AND o.createdAt BETWEEN :windowStart AND :windowEnd")
    List<Order> findInReminderWindow(@Param("status") OrderStatus status,
                                     @Param("windowStart") LocalDateTime windowStart,
                                     @Param("windowEnd") LocalDateTime windowEnd);
}
