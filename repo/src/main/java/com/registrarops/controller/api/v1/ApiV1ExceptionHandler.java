package com.registrarops.controller.api.v1;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standardized error envelope for /api/v1/** endpoints. Only catches validation
 * and argument problems so it does not mask anything else the app already
 * handles.
 */
@RestControllerAdvice(basePackages = "com.registrarops.controller.api.v1")
public class ApiV1ExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ConstraintViolationException ex) {
        return body(HttpStatus.BAD_REQUEST, "validation_failed", ex.getMessage());
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleBodyValidation(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        return body(HttpStatus.BAD_REQUEST, "validation_failed",
                ex.getBindingResult().getAllErrors().toString());
    }

    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<Map<String, Object>> handleBind(
            org.springframework.validation.BindException ex) {
        return body(HttpStatus.BAD_REQUEST, "validation_failed",
                ex.getBindingResult().getAllErrors().toString());
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("timestamp", Instant.now().toString());
        b.put("status", status.value());
        b.put("error", code);
        b.put("message", message);
        return ResponseEntity.status(status).body(b);
    }
}
