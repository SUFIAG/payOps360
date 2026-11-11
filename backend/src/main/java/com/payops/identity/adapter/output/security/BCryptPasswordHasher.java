package com.payops.identity.adapter.output.security;

import com.payops.identity.application.port.output.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt with cost 12 (~250 ms). Passwords longer than 72 bytes are rejected
 * by policy before hashing, avoiding BCrypt's silent truncation.
 */
@Component
class BCryptPasswordHasher implements PasswordHasher {

    private static final int STRENGTH = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(STRENGTH);
    private final String dummyHash = encoder.encode("timing-equaliser-not-a-real-password");

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        if (rawPassword == null || hash == null || rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            encoder.matches("x", dummyHash);
            return false;
        }
        return encoder.matches(rawPassword, hash);
    }

    @Override
    public void simulateVerification() {
        encoder.matches("timing-equaliser-attempt", dummyHash);
    }
}
