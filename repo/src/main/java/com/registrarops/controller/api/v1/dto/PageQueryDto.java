package com.registrarops.controller.api.v1.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Shared request DTO for paginated list endpoints in /api/v1/**.
 * Bean Validation rejects out-of-range inputs before they reach the service,
 * and the resulting {@code ConstraintViolationException} is mapped to a
 * standardized 400 envelope by {@code ApiV1ExceptionHandler}.
 */
public class PageQueryDto {

    @Min(value = 0, message = "page must be >= 0")
    private int page = 0;

    @Min(value = 1, message = "size must be >= 1")
    @Max(value = 200, message = "size must be <= 200")
    private int size = 20;

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
