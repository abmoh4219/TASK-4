package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_grades")
@Getter
@Setter
public class StudentGrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "rule_version_id", nullable = false)
    private Long ruleVersionId;

    @Column(name = "weighted_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightedScore;

    @Column(name = "letter_grade", nullable = false, length = 5)
    private String letterGrade;

    @Column(name = "gpa_points", nullable = false, precision = 3, scale = 2)
    private BigDecimal gpaPoints;

    @Column(name = "credits", nullable = false, precision = 4, scale = 2)
    private BigDecimal credits;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;
}
