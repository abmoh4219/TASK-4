package com.registrarops.repository;

import com.registrarops.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCode(String code);

    List<Course> findByIsActiveTrueOrderByCreatedAtDesc();

    List<Course> findTop10ByIsActiveTrueOrderByCreatedAtDesc();

    List<Course> findTop10ByIsActiveTrueOrderByRatingAvgDesc();

    List<Course> findTop10ByIsActiveTrueOrderByEnrollCountDesc();

    @Query("SELECT c FROM Course c WHERE c.isActive = true AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(c.tags) LIKE LOWER(CONCAT('%', :q, '%')) " +
           " OR LOWER(c.authorName) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Course> searchByTerm(@Param("q") String q);

    @Query("SELECT c FROM Course c WHERE c.isActive = true " +
           "AND (:category IS NULL OR c.category = :category) " +
           "AND (:minPrice IS NULL OR c.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR c.price <= :maxPrice) " +
           "AND (:newSince IS NULL OR c.createdAt >= :newSince)")
    List<Course> filter(@Param("category") String category,
                        @Param("minPrice") BigDecimal minPrice,
                        @Param("maxPrice") BigDecimal maxPrice,
                        @Param("newSince") LocalDateTime newSince);
}
