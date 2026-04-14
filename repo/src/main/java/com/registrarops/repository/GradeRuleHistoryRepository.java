package com.registrarops.repository;

import com.registrarops.entity.GradeRuleHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradeRuleHistoryRepository extends JpaRepository<GradeRuleHistory, Long> {
    List<GradeRuleHistory> findByRuleIdOrderByChangedAtDesc(Long ruleId);
}
