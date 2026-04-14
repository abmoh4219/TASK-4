package com.registrarops.controller.api.v1.dto;

import jakarta.validation.constraints.Min;

/**
 * Query DTO for {@code GET /api/v1/grades}. Adds optional object-scoping
 * filter {@code courseId} on top of {@link PageQueryDto}.
 */
public class GradeListQueryDto extends PageQueryDto {

    @Min(value = 1, message = "courseId must be >= 1")
    private Long courseId;

    public Long getCourseId() { return courseId; }
    public void setCourseId(Long courseId) { this.courseId = courseId; }
}
