package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "sender_type", nullable = false, length = 50)
    private String senderType = "SYSTEM";

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "subject", nullable = false, length = 500)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "related_type", length = 50)
    private String relatedType;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = Boolean.FALSE;

    @Column(name = "thread_key", length = 200)
    private String threadKey;

    @Column(name = "thread_count", nullable = false)
    private Integer threadCount = 1;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deliver_at")
    private LocalDateTime deliverAt;
}
