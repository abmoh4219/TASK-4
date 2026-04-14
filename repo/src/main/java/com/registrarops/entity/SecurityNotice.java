package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "security_notices")
@Getter
@Setter
public class SecurityNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notice_type", nullable = false, length = 50)
    private String noticeType;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
