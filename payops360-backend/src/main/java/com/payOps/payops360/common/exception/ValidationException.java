package com.payops.payops360.common.exception;

/**
 * Exception thrown when validation of business rules fails.
 */
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String message, Object... args) {
        super("VALIDATION_ERROR", message, args);
    }
}

