package com.registrarops.repository;

import com.registrarops.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * APPEND-ONLY by design. Every action creates a new record.
 *
 * This repository INTENTIONALLY does not extend JpaRepository or CrudRepository.
 * It exposes only:
 *   - a single {@code save(AuditLog)} method for inserts of brand-new entities
 *   - read-only {@code findBy*} / paging queries
 *
 * There is NO update method, NO delete method, NO deleteAll, NO saveAll on
 * existing rows. The audit table is the immutable system-of-record for who did
 * what and when. Modifying it would defeat its purpose.
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    /** Insert a brand-new audit record. Callers MUST pass an entity with id == null. */
    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(Long id);

    Page<AuditLog> findAll(Pageable pageable);

    List<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :from AND :to ORDER BY a.createdAt DESC")
    List<AuditLog> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long count();
}
