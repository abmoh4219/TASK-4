package com.registrarops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_terms")
@Getter
@Setter
public class SearchTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "term", nullable = false, unique = true, length = 255)
    private String term;

    @Column(name = "search_count", nullable = false)
    private Integer searchCount = 0;

    @Column(name = "last_searched_at", nullable = false)
    private LocalDateTime lastSearchedAt;
}
