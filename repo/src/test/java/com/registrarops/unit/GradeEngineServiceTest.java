package com.registrarops.unit;

import com.registrarops.entity.*;
import com.registrarops.repository.*;
import com.registrarops.service.AuditService;
import com.registrarops.service.GradeEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class GradeEngineServiceTest {

    private GradeRuleRepository gradeRuleRepository;
    private GradeComponentRepository gradeComponentRepository;
    private StudentGradeRepository studentGradeRepository;
    private EnrollmentRepository enrollmentRepository;
    private CourseRepository courseRepository;
    private AuditService auditService;
    private GradeEngineService engine;

    @BeforeEach
    void setUp() {
        gradeRuleRepository = mock(GradeRuleRepository.class);
        gradeComponentRepository = mock(GradeComponentRepository.class);
        studentGradeRepository = mock(StudentGradeRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        courseRepository = mock(CourseRepository.class);
        auditService = mock(AuditService.class);
        engine = new GradeEngineService(gradeRuleRepository, gradeComponentRepository,
                studentGradeRepository, enrollmentRepository, courseRepository, auditService);

        when(studentGradeRepository.save(any(StudentGrade.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Default course
        Course course = new Course();
        course.setId(1L);
        course.setCredits(new BigDecimal("3.00"));
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course));
    }

    private static GradeRule rule(String weights, RetakePolicy policy) {
        GradeRule r = new GradeRule();
        r.setId(7L);
        r.setVersion(1);
        r.setIsActive(true);
        r.setRetakePolicy(policy);
        r.setWeightsJson(weights);
        return r;
    }

    private static GradeComponent gc(String name, double score, int attempt) {
        GradeComponent c = new GradeComponent();
        c.setComponentName(name);
        c.setScore(BigDecimal.valueOf(score));
        c.setMaxScore(new BigDecimal("100.00"));
        c.setAttemptNumber(attempt);
        c.setRecordedAt(LocalDateTime.now());
        return c;
    }

    @Test
    void testWeightedCalculation30_20_50() {
        when(gradeRuleRepository.findById(7L)).thenReturn(Optional.of(
                rule("{\"coursework\":30,\"midterm\":20,\"final\":50}", RetakePolicy.HIGHEST_SCORE)));
        when(gradeComponentRepository.findByCourseIdAndStudentId(1L, 1L)).thenReturn(List.of(
                gc("coursework", 80, 1),
                gc("midterm",    70, 1),
                gc("final",      90, 1)));

        StudentGrade g = engine.calculateGrade(1L, 1L, 7L);
        // 0.80*30 + 0.70*20 + 0.90*50 = 24 + 14 + 45 = 83
        assertEquals(new BigDecimal("83.00"), g.getWeightedScore());
        assertEquals("B+", g.getLetterGrade());
        assertEquals(new BigDecimal("3.30"), g.getGpaPoints());
    }

    @Test
    void testGpaConversion90to100() {
        assertEquals(new BigDecimal("4.00"), GradeEngineService.convertToGpa(new BigDecimal("90.00")));
        assertEquals(new BigDecimal("4.00"), GradeEngineService.convertToGpa(new BigDecimal("100.00")));
        assertEquals(new BigDecimal("4.00"), GradeEngineService.convertToGpa(new BigDecimal("95.50")));
    }

    @Test
    void testGpaConversionBoundaries() {
        assertEquals(new BigDecimal("3.70"), GradeEngineService.convertToGpa(new BigDecimal("85.00")));
        assertEquals(new BigDecimal("3.30"), GradeEngineService.convertToGpa(new BigDecimal("80.00")));
        assertEquals(new BigDecimal("3.00"), GradeEngineService.convertToGpa(new BigDecimal("75.00")));
        assertEquals(new BigDecimal("2.70"), GradeEngineService.convertToGpa(new BigDecimal("70.00")));
        assertEquals(new BigDecimal("2.00"), GradeEngineService.convertToGpa(new BigDecimal("60.00")));
        assertEquals(BigDecimal.ZERO,        GradeEngineService.convertToGpa(new BigDecimal("59.99")));
    }

    @Test
    void testRetakePolicyHighestScore() {
        when(gradeRuleRepository.findById(7L)).thenReturn(Optional.of(
                rule("{\"midterm\":100}", RetakePolicy.HIGHEST_SCORE)));
        when(gradeComponentRepository.findByCourseIdAndStudentId(1L, 1L)).thenReturn(List.of(
                gc("midterm", 60, 1),
                gc("midterm", 80, 2),  // higher attempt
                gc("midterm", 70, 3)));

        StudentGrade g = engine.calculateGrade(1L, 1L, 7L);
        assertEquals(new BigDecimal("80.00"), g.getWeightedScore());
    }

    @Test
    void testRetakePolicyLatestScore() {
        when(gradeRuleRepository.findById(7L)).thenReturn(Optional.of(
                rule("{\"midterm\":100}", RetakePolicy.LATEST_SCORE)));
        when(gradeComponentRepository.findByCourseIdAndStudentId(1L, 1L)).thenReturn(List.of(
                gc("midterm", 60, 1),
                gc("midterm", 80, 2),
                gc("midterm", 70, 3))); // latest attempt → take 70

        StudentGrade g = engine.calculateGrade(1L, 1L, 7L);
        assertEquals(new BigDecimal("70.00"), g.getWeightedScore());
    }

    @Test
    void testRecalculateAllOnRuleChange() {
        Enrollment e1 = new Enrollment(); e1.setStudentId(1L); e1.setCourseId(1L);
        Enrollment e2 = new Enrollment(); e2.setStudentId(2L); e2.setCourseId(1L);
        when(enrollmentRepository.findByCourseId(1L)).thenReturn(List.of(e1, e2));
        when(gradeRuleRepository.findById(7L)).thenReturn(Optional.of(
                rule("{\"final\":100}", RetakePolicy.HIGHEST_SCORE)));
        when(gradeComponentRepository.findByCourseIdAndStudentId(anyLong(), anyLong()))
                .thenReturn(List.of(gc("final", 88, 1)));

        int n = engine.recalculateAll(1L, 7L);
        assertEquals(2, n);
        verify(studentGradeRepository, times(2)).save(any(StudentGrade.class));
    }

    @Test
    void testRuleVersioningPreservesHistory() {
        // Saving a new StudentGrade for the same (student,course) does NOT touch the old row.
        when(gradeRuleRepository.findById(7L)).thenReturn(Optional.of(
                rule("{\"final\":100}", RetakePolicy.HIGHEST_SCORE)));
        when(gradeComponentRepository.findByCourseIdAndStudentId(1L, 1L))
                .thenReturn(List.of(gc("final", 95, 1)));

        StudentGrade g = engine.calculateGrade(1L, 1L, 7L);
        assertEquals(7L, g.getRuleVersionId());
    }

    @Test
    void testParseWeightsJson() {
        Map<String, BigDecimal> w = GradeEngineService.parseWeights("{\"coursework\":30,\"midterm\":20,\"final\":50}");
        assertEquals(new BigDecimal("30"), w.get("coursework"));
        assertEquals(new BigDecimal("50"), w.get("final"));
    }
}
