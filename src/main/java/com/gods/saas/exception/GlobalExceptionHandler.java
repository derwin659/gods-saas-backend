package com.gods.saas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handleBusiness(BusinessException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case "SUBSCRIPTION_EXPIRED", "SUBSCRIPTION_INACTIVE" -> HttpStatus.FORBIDDEN;
            case "PLAN_LIMIT_BRANCHES", "PLAN_LIMIT_BARBERS", "PLAN_LIMIT_ADMINS" -> HttpStatus.CONFLICT;
            case "SUBSCRIPTION_NOT_FOUND", "PAYMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "PLAN_INVALID", "INVALID_AMOUNT", "PAYMENT_ALREADY_PENDING", "PAYMENT_ALREADY_REVIEWED" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };

        return ResponseEntity.status(status).body(Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "code", "BAD_REQUEST",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", "INTERNAL_ERROR",
                "message", ex.getMessage() == null ? "Ocurrió un error interno" : ex.getMessage(),
                "timestamp", LocalDateTime.now()
        ));
    }
}