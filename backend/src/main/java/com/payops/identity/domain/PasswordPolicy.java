package com.payops.identity.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules aligned with NIST SP 800-63B and PCI DSS v4.0 req. 8.3.6:
 * minimum length, character diversity, no personal data and a deny-list of
 * commonly breached passwords.
 */
public record PasswordPolicy(int minLength, int historyDepth) {

    /** BCrypt only uses the first 72 bytes; longer secrets are rejected instead of silently truncated. */
    private static final int MAX_BYTES = 72;
    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "123456789012", "qwertyuiop", "letmein",
            "welcome", "admin", "administrator", "changeme", "iloveyou", "football", "monkey",
            "dragon", "sunshine", "princess", "passw0rd", "p@ssw0rd", "qwerty123", "abc123");

    public List<String> violations(String password, String email, String fullName) {
        List<String> violations = new ArrayList<>();
        if (password == null || password.length() < minLength) {
            violations.add("Must be at least " + minLength + " characters");
            return violations;
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            violations.add("Must be at most " + MAX_BYTES + " bytes long");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            violations.add("Must contain an uppercase letter");
        }
        if (password.chars().noneMatch(Character::isLowerCase)) {
            violations.add("Must contain a lowercase letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            violations.add("Must contain a digit");
        }
        if (password.chars().allMatch(Character::isLetterOrDigit)) {
            violations.add("Must contain a special character");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.stream().anyMatch(lower::contains)) {
            violations.add("Must not contain a commonly used password");
        }
        if (email != null) {
            String localPart = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (localPart.length() >= 3 && lower.contains(localPart)) {
                violations.add("Must not contain your email address");
            }
        }
        if (fullName != null) {
            for (String part : fullName.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (part.length() >= 3 && lower.contains(part)) {
                    violations.add("Must not contain your name");
                    break;
                }
            }
        }
        if (hasLongRun(password)) {
            violations.add("Must not repeat the same character 4 or more times in a row");
        }
        return violations;
    }

    public void enforce(String password, String email, String fullName) {
        List<String> violations = violations(password, email, fullName);
        if (!violations.isEmpty()) {
            throw new WeakPasswordException(violations);
        }
    }

    private static boolean hasLongRun(String password) {
        int run = 1;
        for (int i = 1; i < password.length(); i++) {
            run = password.charAt(i) == password.charAt(i - 1) ? run + 1 : 1;
            if (run >= 4) {
                return true;
            }
        }
        return false;
    }
}
