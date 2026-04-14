package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "evaluation_cycles")
@Getter
@Setter
public class EvaluationCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "faculty_id", nullable = false)
    private Long facultyId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EvaluationStatus status = EvaluationStatus.DRAFT;

    @Column(name = "reviewer_comment", length = 2000)
    private String reviewerComment;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
