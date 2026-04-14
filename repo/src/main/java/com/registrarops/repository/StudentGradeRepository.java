package com.registrarops.repository;

import com.registrarops.entity.StudentGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentGradeRepository extends JpaRepository<StudentGrade, Long> {
    List<StudentGrade> findByStudentIdOrderByCalculatedAtDesc(Long studentId);
    List<StudentGrade> findByCourseId(Long courseId);
}
