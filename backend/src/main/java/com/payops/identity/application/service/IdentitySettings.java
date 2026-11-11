package com.payops.identity.application.service;

import com.payops.identity.domain.LockoutPolicy;
import com.payops.identity.domain.PasswordPolicy;

import java.time.Duration;

/**
 * Plain settings object so application services stay framework-free.
 */
public record IdentitySettings(
        LockoutPolicy lockoutPolicy,
        PasswordPolicy passwordPolicy,
        Duration passwordMaxAge,
        Duration refreshTokenTtl,
        int maxIpFailuresPer15Minutes,
        String mfaIssuer) {
}
