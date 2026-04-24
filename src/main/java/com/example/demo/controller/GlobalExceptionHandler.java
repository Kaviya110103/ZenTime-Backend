package com.example.demo.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        String raw = ex == null
                ? null
                : ex.getMostSpecificCause() == null
                        ? ex.getMessage()
                        : ex.getMostSpecificCause().getMessage();
        String message = resolveDuplicateMessage(raw);
        return buildError(HttpStatus.CONFLICT, message, request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String message = "Invalid request data";
        if (ex != null && ex.getBindingResult() != null && ex.getBindingResult().hasErrors()) {
            FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
            if (fieldError != null) {
                String defaultMessage = fieldError.getDefaultMessage();
                String fieldName = fieldError.getField();
                if (defaultMessage != null && !defaultMessage.isBlank()) {
                    message = fieldName + ": " + defaultMessage;
                } else {
                    message = fieldName + " is invalid";
                }
            }
        }
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        String message = "Invalid request data";
        if (ex != null && ex.getConstraintViolations() != null && !ex.getConstraintViolations().isEmpty()) {
            String violationMessage = ex.getConstraintViolations().iterator().next().getMessage();
            if (violationMessage != null && !violationMessage.isBlank()) {
                message = violationMessage;
            }
        }
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {
        String param = ex == null ? "Required parameter" : ex.getParameterName();
        return buildError(HttpStatus.BAD_REQUEST, param + " is required", request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Invalid request format", request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        String message = sanitizeMessage(ex == null ? null : ex.getMessage(), "Invalid request");
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {
        String message = sanitizeMessage(ex == null ? null : ex.getMessage(), "Request could not be processed");
        return buildError(HttpStatus.CONFLICT, message, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex,
            HttpServletRequest request) {
        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please try again.",
                request.getRequestURI());
    }

    private ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("message", message);
        body.put("path", path);
        return ResponseEntity.status(status).body(body);
    }

    private String resolveDuplicateMessage(String rawMessage) {
        String normalized = rawMessage == null ? "" : rawMessage.toLowerCase();
        if (normalized.contains("email") || normalized.contains("email_id")) {
            return "Email already exists";
        }
        if (normalized.contains("username")) {
            return "Username already exists";
        }
        if (normalized.contains("employee_code")) {
            return "Employee code already exists";
        }
        if (normalized.contains("company_code")) {
            return "Company code already exists";
        }
        if (normalized.contains("mobile") || normalized.contains("mobile_number")) {
            return "Mobile number already exists";
        }
        return "Duplicate value already exists";
    }

    private String sanitizeMessage(String rawMessage, String fallback) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return fallback;
        }
        String normalized = rawMessage.toLowerCase();
        if (normalized.contains("duplicate entry")
                || normalized.contains("sql")
                || normalized.contains("constraint")
                || normalized.contains("jdbc")
                || normalized.contains("hibernate")
                || normalized.contains("syntax error")) {
            return fallback;
        }
        return rawMessage;
    }
}

