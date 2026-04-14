package com.registrarops.repository;

import com.registrarops.entity.GradeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GradeRuleRepository extends JpaRepository<GradeRule, Long> {
    Optional<GradeRule> findFirstByCourseIdAndIsActiveTrueOrderByVersionDesc(Long courseId);
    List<GradeRule> findByCourseIdOrderByVersionDesc(Long courseId);
}
