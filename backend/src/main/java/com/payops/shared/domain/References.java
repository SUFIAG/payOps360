package com.payops.shared.domain;

import java.security.SecureRandom;

/**
 * Generates human friendly, collision resistant business references
 * such as {@code PAY-7F3K9Q2M8XWD}.
 */
public final class References {

    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private References() {
    }

    public static String next(String prefix) {
        char[] chars = new char[12];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return prefix + "-" + new String(chars);
    }
}
