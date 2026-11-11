package com.payops.identity.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "payops.security")
public record SecurityProperties(
        Jwt jwt,
        Duration refreshTokenTtl,
        String dataEncryptionKey,
        boolean cookieSecure,
        List<String> allowedOrigins,
        Lockout lockout,
        RateLimit rateLimit,
        Password password) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration mfaTokenTtl) {
    }

    public record Lockout(int maxFailedAttempts, Duration lockDuration, int maxIpFailuresPer15Minutes) {
    }

    public record RateLimit(int loginPerIpPerMinute, int apiPerUserPerMinute) {
    }

    public record Password(int minLength, int historyDepth, int maxAgeDays) {
    }
}
