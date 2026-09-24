package com.tindev.exceptions;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ApiError body = new ApiError(java.time.Instant.now(), 400, "VALIDATION_ERROR",
                "Dữ liệu gửi lên không hợp lệ", request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception, HttpServletRequest request) {
        return error(exception.status(), exception.status().name(), exception.getMessage(), request);
    }

    @ExceptionHandler(UserException.class)
    ResponseEntity<ApiError> handleUser(UserException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "REQUEST_ERROR", exception.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONFLICT", "Dữ liệu vi phạm ràng buộc duy nhất hoặc quan hệ phụ thuộc", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Đã xảy ra lỗi không mong muốn", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String error, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), error, message, request.getRequestURI()));
    }
}
