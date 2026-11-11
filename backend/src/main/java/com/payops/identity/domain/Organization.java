package com.payops.identity.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public record Organization(Long id, UUID publicId, String name, String slug, boolean mfaRequired,
                           boolean aiEnabled, String baseCurrency) {

    private static final int MAX_SLUG_BASE = 60;

    /** URL-friendly identifier derived from the name, e.g. "Acme Payments Ltd." → "acme-payments-ltd". */
    public static String slugify(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (ascii.length() > MAX_SLUG_BASE) {
            ascii = ascii.substring(0, MAX_SLUG_BASE).replaceAll("-+$", "");
        }
        return ascii.isEmpty() ? "org" : ascii;
    }
}
