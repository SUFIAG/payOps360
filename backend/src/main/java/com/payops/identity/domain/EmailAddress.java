package com.payops.identity.domain;

import com.payops.shared.domain.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalisation and syntactic validation of login e-mail addresses
 * (trimmed, lower-cased; uniqueness is global across tenants).
 */
public final class EmailAddress {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}\\.[A-Za-z]{2,}$");

    private EmailAddress() {
    }

    public static String normalize(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new DomainException("INVALID_EMAIL", "Email address is not valid");
        }
        return normalized;
    }
}
