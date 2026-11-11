package com.payops.identity.application.port.in;

/**
 * Secret shown once to the user so an authenticator app can be configured.
 */
public record MfaEnrollment(String secret, String otpauthUri) {
}
