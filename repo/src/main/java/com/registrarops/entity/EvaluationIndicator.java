package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "evaluation_indicators")
@Getter
@Setter
public class EvaluationIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "indicator_name", nullable = false, length = 200)
    private String indicatorName;

    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal weight = BigDecimal.ZERO;

    @Column(name = "score", precision = 5, scale = 2)
    private BigDecimal score;

    @Column(name = "mean_score", precision = 5, scale = 2)
    private BigDecimal meanScore;

    @Column(name = "std_dev", precision = 5, scale = 2)
    private BigDecimal stdDev;

    @Column(name = "is_outlier", nullable = false)
    private Boolean isOutlier = Boolean.FALSE;
}
