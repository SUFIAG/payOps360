package com.payops.identity.application.port.output;

/**
 * Authenticated encryption for secrets at rest (e.g. TOTP seeds).
 */
public interface SecretCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
