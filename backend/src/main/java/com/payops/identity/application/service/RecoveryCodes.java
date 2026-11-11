package com.payops.identity.application.service;

import com.payops.identity.application.port.output.RecoveryCodeRepository;
import com.payops.identity.application.port.output.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Single-use MFA recovery codes (format XXXXX-XXXXX); only hashes are stored.
 */
@Component
@RequiredArgsConstructor
class RecoveryCodes {

    private static final int COUNT = 10;
    private static final char[] ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RecoveryCodeRepository repository;
    private final TokenService tokens;

    List<String> regenerate(long userId) {
        List<String> codes = new ArrayList<>(COUNT);
        List<String> hashes = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            String code = randomChunk() + "-" + randomChunk();
            codes.add(code);
            hashes.add(tokens.hashOpaqueToken(code));
        }
        repository.replaceAll(userId, hashes);
        return codes;
    }

    boolean consume(long userId, String code) {
        if (code == null || !code.matches("(?i)[a-z0-9]{5}-[a-z0-9]{5}")) {
            return false;
        }
        return repository.consume(userId, tokens.hashOpaqueToken(code.toLowerCase(Locale.ROOT)));
    }

    void deleteAll(long userId) {
        repository.deleteAll(userId);
    }

    private static String randomChunk() {
        char[] chunk = new char[5];
        for (int i = 0; i < chunk.length; i++) {
            chunk[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(chunk);
    }
}
