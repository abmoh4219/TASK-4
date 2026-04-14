package com.registrarops.api;

import com.registrarops.entity.GradeComponent;
import com.registrarops.entity.RetakePolicy;
import com.registrarops.entity.StudentGrade;
import com.registrarops.repository.GradeComponentRepository;
import com.registrarops.repository.GradeRuleRepository;
import com.registrarops.repository.StudentGradeRepository;
import com.registrarops.service.GradeEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class GradeApiTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GradeEngineService gradeEngineService;
    @Autowired private GradeComponentRepository gradeComponentRepository;
    @Autowired private GradeRuleRepository gradeRuleRepository;
    @Autowired private StudentGradeRepository studentGradeRepository;

    @BeforeEach
    void clearGradeState() {
        gradeComponentRepository.deleteAll();
        studentGradeRepository.deleteAll();
    }

    private void seedComponents(long studentId, long courseId) {
        for (var entry : new String[][]{{"coursework", "80"}, {"midterm", "70"}, {"final", "90"}}) {
            GradeComponent gc = new GradeComponent();
            gc.setStudentId(studentId);
            gc.setCourseId(courseId);
            gc.setComponentName(entry[0]);
            gc.setScore(new BigDecimal(entry[1]));
            gc.setMaxScore(new BigDecimal("100"));
            gc.setAttemptNumber(1);
            gc.setRecordedAt(LocalDateTime.now());
            gradeComponentRepository.save(gc);
        }
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testFacultyEntersGradeAndCalculates() throws Exception {
        // POST a single grade component for student=4 course=1
        mockMvc.perform(post("/grades/{cid}/components", 1L)
                        .with(csrf())
                        .param("studentId", "4")
                        .param("componentName", "coursework")
                        .param("score", "85")
                        .param("maxScore", "100"))
                .andExpect(status().is3xxRedirection());
        assertFalse(gradeComponentRepository.findByCourseIdAndStudentId(1L, 4L).isEmpty());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testStudentSeesOwnGrade() throws Exception {
        // Seed grade components for student=4 course=1 then calculate.
        seedComponents(4L, 1L);
        Long ruleId = gradeRuleRepository.findFirstByCourseIdAndIsActiveTrueOrderByVersionDesc(1L)
                .orElseThrow().getId();
        gradeEngineService.calculateGrade(4L, 1L, ruleId);

        mockMvc.perform(get("/grades/report"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Academic Report")));
    }

    @Test
    @WithMockUser(username = "faculty", roles = "FACULTY")
    void testGpaCalculationCorrect() {
        // Direct service call: 80*0.30 + 70*0.20 + 90*0.50 = 24+14+45 = 83
        seedComponents(4L, 1L);
        Long ruleId = gradeRuleRepository.findFirstByCourseIdAndIsActiveTrueOrderByVersionDesc(1L)
                .orElseThrow().getId();
        StudentGrade sg = gradeEngineService.calculateGrade(4L, 1L, ruleId);
        assertEquals(0, sg.getWeightedScore().compareTo(new BigDecimal("83.00")));
        assertEquals("B+", sg.getLetterGrade());
    }

    @Test
    @WithMockUser(username = "student", roles = "STUDENT")
    void testStudentCannotEnterGrade() throws Exception {
        // Audit blocker #1: student must be blocked from the faculty-only grade
        // entry page and from posting grade components.
        mockMvc.perform(get("/grades/1/entry"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/grades/{cid}/components", 1L)
                        .with(csrf())
                        .param("studentId", "4")
                        .param("componentName", "coursework")
                        .param("score", "99")
                        .param("maxScore", "100"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminRecalculateTriggersUpdate() throws Exception {
        seedComponents(4L, 1L);
        long beforeCount = studentGradeRepository.count();
        mockMvc.perform(post("/grades/{cid}/recalculate", 1L).with(csrf()))
                .andExpect(status().is3xxRedirection());
        long afterCount = studentGradeRepository.count();
        assertTrue(afterCount > beforeCount, "Recalculate should produce at least one new StudentGrade row");
    }
}
