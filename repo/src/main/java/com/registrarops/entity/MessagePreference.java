package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "message_preferences")
@Getter
@Setter
public class MessagePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** Comma-separated category names. */
    @Column(name = "muted_categories", nullable = false, length = 500)
    private String mutedCategories = "";

    @Column(name = "quiet_start_hour", nullable = false)
    private Integer quietStartHour = 22;

    @Column(name = "quiet_end_hour", nullable = false)
    private Integer quietEndHour = 7;
}
