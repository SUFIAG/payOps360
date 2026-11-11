package com.payops.identity.domain;

import com.payops.shared.domain.UnauthorizedException;

public class AuthenticationFailedException extends UnauthorizedException {

    public static final String GENERIC_MESSAGE = "Invalid credentials";

    public AuthenticationFailedException() {
        super("INVALID_CREDENTIALS", GENERIC_MESSAGE);
    }

    public AuthenticationFailedException(String code, String message) {
        super(code, message);
    }
}
