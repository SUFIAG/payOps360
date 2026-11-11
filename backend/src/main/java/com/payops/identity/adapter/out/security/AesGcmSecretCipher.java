package com.payops.identity.adapter.out.security;

import com.payops.identity.adapter.config.SecurityProperties;
import com.payops.identity.application.port.out.SecretCipher;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM with a random 96-bit nonce per message. Output format:
 * {@code v1:base64(nonce || ciphertext || tag)}. The version prefix allows
 * future key rotation or a move to a KMS/HSM-backed implementation.
 */
@Component
class AesGcmSecretCipher implements SecretCipher {

    private static final String VERSION = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    AesGcmSecretCipher(SecurityProperties properties) {
        String encoded = properties.dataEncryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("payops.security.data-encryption-key (DATA_ENCRYPTION_KEY) must be set");
        }
        byte[] raw = Base64.getDecoder().decode(encoded);
        if (raw.length != 32) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY must be a base64-encoded 256-bit key");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String value) {
        if (value == null || !value.startsWith(VERSION)) {
            throw new IllegalArgumentException("Unsupported ciphertext format");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, payload, 0, NONCE_BYTES));
            byte[] plaintext = cipher.doFinal(payload, NONCE_BYTES, payload.length - NONCE_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
