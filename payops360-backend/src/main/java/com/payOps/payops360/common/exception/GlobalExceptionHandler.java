package com.payops.payops360.common.exception;

import com.payops.payops360.common.dto.ApiResponse;
import com.payops.payops360.common.dto.ErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for the entire application.
 * Provides consistent error responses across all REST endpoints.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle business logic exceptions
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.warn("Business exception: {} - Request ID: {}", ex.getMessage(), requestId, ex);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.warn("Resource not found: {} - Request ID: {}", ex.getMessage(), requestId);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle validation exceptions
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        Map<String, Object> fieldErrors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {} - Request ID: {}", fieldErrors, requestId);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("Validation failed for one or more fields")
                .details(fieldErrors)
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle bad credentials (wrong password)
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.warn("Bad credentials: {} - Request ID: {}", ex.getMessage(), requestId);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code("BAD_CREDENTIALS")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle authentication exceptions
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.warn("Authentication failed: {} - Request ID: {}", ex.getMessage(), requestId);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code("AUTHENTICATION_FAILED")
                .message("Authentication failed: " + ex.getMessage())
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle illegal argument exceptions (e.g., email already exists)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.warn("Illegal argument: {} - Request ID: {}", ex.getMessage(), requestId);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code("INVALID_ARGUMENT")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle illegal state exceptions (e.g., account locked)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(
            IllegalStateException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.warn("Illegal state: {} - Request ID: {}", ex.getMessage(), requestId);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code("INVALID_STATE")
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle access denied exceptions
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.warn("Access denied: {} - Request ID: {}", ex.getMessage(), requestId);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code("ACCESS_DENIED")
                .message("You do not have permission to access this resource")
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        String requestId = UUID.randomUUID().toString();
        log.error("Unexpected error occurred - Request ID: {}", requestId, ex);

        ErrorDetail errorDetail = ErrorDetail.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred. Please try again later.")
                .timestamp(Instant.now())
                .build();

        ApiResponse<Void> response = ApiResponse.error(errorDetail, requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

