package com.registrarops.repository;

import com.registrarops.entity.EvaluationIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationIndicatorRepository extends JpaRepository<EvaluationIndicator, Long> {
    List<EvaluationIndicator> findByCycleId(Long cycleId);
}
