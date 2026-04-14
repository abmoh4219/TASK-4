package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * APPEND-ONLY audit record. Once persisted, the application MUST NOT update or delete a row.
 * AuditLogRepository exposes only inserts and read-only queries (see its Javadoc).
 *
 * Note: there is intentionally NO {@code @PreUpdate}, NO {@code @UpdateTimestamp}, and
 * NO {@code updated_at} column. Modifying an audit row would defeat the purpose.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "action", nullable = false, length = 200)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "old_value_masked", columnDefinition = "TEXT")
    private String oldValueMasked;

    @Column(name = "new_value_masked", columnDefinition = "TEXT")
    private String newValueMasked;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
