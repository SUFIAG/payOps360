package com.payops.shared.domain;

/**
 * Authentication could not be established. Messages must stay generic so
 * they never reveal whether an account exists.
 */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
