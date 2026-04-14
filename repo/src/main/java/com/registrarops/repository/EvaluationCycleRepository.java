package com.registrarops.repository;

import com.registrarops.entity.EvaluationCycle;
import com.registrarops.entity.EvaluationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationCycleRepository extends JpaRepository<EvaluationCycle, Long> {
    List<EvaluationCycle> findByFacultyIdOrderByCreatedAtDesc(Long facultyId);
    List<EvaluationCycle> findByStatus(EvaluationStatus status);
}
