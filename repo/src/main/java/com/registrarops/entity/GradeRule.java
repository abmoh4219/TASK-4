package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "grade_rules")
@Getter
@Setter
public class GradeRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "retake_policy", nullable = false, length = 30)
    private RetakePolicy retakePolicy = RetakePolicy.HIGHEST_SCORE;

    /** JSON object string, e.g. {"coursework":30,"midterm":20,"final":50}. */
    @Column(name = "weights_json", nullable = false, length = 2000)
    private String weightsJson;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
