package com.registrarops.repository;

import com.registrarops.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT COUNT(m) FROM Message m WHERE m.recipientId = :userId AND m.isRead = false " +
           "AND (m.deliverAt IS NULL OR m.deliverAt <= :now)")
    long countUnreadDelivered(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT m FROM Message m WHERE m.recipientId = :userId " +
           "AND (m.deliverAt IS NULL OR m.deliverAt <= :now) " +
           "ORDER BY m.createdAt DESC")
    List<Message> findDeliveredForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Query("SELECT m FROM Message m WHERE m.recipientId = :userId AND m.category = :category " +
           "AND m.relatedType = :relatedType AND m.relatedId = :relatedId " +
           "AND m.createdAt >= :since ORDER BY m.createdAt DESC")
    Optional<Message> findRecentDuplicate(@Param("userId") Long userId,
                                          @Param("category") String category,
                                          @Param("relatedType") String relatedType,
                                          @Param("relatedId") Long relatedId,
                                          @Param("since") LocalDateTime since);
}
