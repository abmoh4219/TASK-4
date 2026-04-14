package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "courses")
@Getter
@Setter
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "credits", nullable = false, precision = 4, scale = 2)
    private BigDecimal credits = new BigDecimal("3.00");

    @Column(name = "category", length = 100)
    private String category;

    /** Comma-separated tags. */
    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "author_name", length = 200)
    private String authorName;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "rating_avg", nullable = false, precision = 3, scale = 2)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "enroll_count", nullable = false)
    private Integer enrollCount = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = Boolean.TRUE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
