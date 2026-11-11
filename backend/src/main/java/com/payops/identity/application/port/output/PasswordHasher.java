package com.payops.identity.application.port.output;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);

    /**
     * Burns the same CPU time as a real verification. Used when the account
     * does not exist so response timing does not reveal valid emails.
     */
    void simulateVerification();
}
