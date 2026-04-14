package com.registrarops.repository;

import com.registrarops.entity.GradeComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GradeComponentRepository extends JpaRepository<GradeComponent, Long> {
    List<GradeComponent> findByCourseIdAndStudentId(Long courseId, Long studentId);
    List<GradeComponent> findByCourseId(Long courseId);

    /** Faculty scoping: true if this faculty has recorded any component for this student. */
    boolean existsByStudentIdAndRecordedBy(Long studentId, Long recordedBy);

    /** Faculty scoping: true if this faculty has recorded any component in this course. */
    boolean existsByCourseIdAndRecordedBy(Long courseId, Long recordedBy);
}
