package com.payops.shared.domain;

/**
 * Base type for every business rule violation. Carries a stable, machine
 * readable code that the web layer exposes to clients.
 */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
