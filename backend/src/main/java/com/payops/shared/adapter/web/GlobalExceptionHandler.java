package com.payops.shared.adapter.web;

import com.payops.shared.domain.ConflictException;
import com.payops.shared.domain.DomainException;
import com.payops.shared.domain.NotFoundException;
import com.payops.shared.domain.UnauthorizedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps exceptions to the API error envelope. Internal details never leave the
 * server: unexpected errors are logged with the correlation id and the client
 * only receives a generic message plus that id.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.code(), ex.getMessage(), null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiResponse<Void>> authFailed(UnauthorizedException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.code(), ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiResponse<Void>> conflict(ConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.code(), ex.getMessage(), null);
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiResponse<Void>> domain(DomainException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.code(), ex.getMessage(), null);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiResponse<Void>> rateLimited(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
                .body(ApiResponse.fail(new ApiError("RATE_LIMITED", "Too many requests. Try again later.", null, correlationId())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", fields);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class,
            MissingRequestCookieException.class})
    ResponseEntity<ApiResponse<Void>> badRequest(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "The request is malformed", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The uploaded file exceeds the size limit", null);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ResponseEntity<ApiResponse<Void>> badMultipart(Exception ex) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "A file upload is required", null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    ResponseEntity<ApiResponse<Void>> forbidden(Exception ex) {
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action", null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiResponse<Void>> concurrentUpdate(OptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "The resource was modified concurrently. Reload and retry.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> noRoute(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String code, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(ApiResponse.fail(new ApiError(code, message, fields, correlationId())));
    }

    private static String correlationId() {
        return MDC.get(CorrelationIdFilter.MDC_KEY);
    }
}
