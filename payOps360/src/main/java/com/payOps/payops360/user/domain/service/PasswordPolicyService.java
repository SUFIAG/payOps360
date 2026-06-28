package com.payOps.payops360.user.domain.service;

import com.payOps.payops360.user.domain.model.User;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Password policy enforcement service
 * Implements your requirements:
 * - 12+ character minimum
 * - Passphrase support
 * - No arbitrary complexity rules
 * - Block common/leaked passwords
 */
@Slf4j
public class PasswordPolicyService {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;

    // Common weak passwords (simplified list - in production, use HaveIBeenPwned API)
    private static final Pattern[] WEAK_PATTERNS = {
        Pattern.compile("^password.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^12345.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^qwerty.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^admin.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^letmein.*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^welcome.*", Pattern.CASE_INSENSITIVE)
    };

    // Common dictionary words to block
    private static final String[] DICTIONARY_WORDS = {
        "password", "username", "admin", "root", "system",
        "payops", "fintech", "payment", "banking"
    };

    /**
     * Validates password against policy
     * @param password Plain text password
     * @param user User context (to prevent using personal info)
     * @return ValidationResult with errors if any
     */
    public PasswordValidationResult validate(String password, User user) {
        PasswordValidationResult result = new PasswordValidationResult();

        // Length check
        if (password == null || password.length() < MIN_LENGTH) {
            result.addError("Password must be at least " + MIN_LENGTH + " characters long");
        }

        if (password != null && password.length() > MAX_LENGTH) {
            result.addError("Password must not exceed " + MAX_LENGTH + " characters");
        }

        // Weak password patterns
        for (Pattern pattern : WEAK_PATTERNS) {
            if (pattern.matcher(password).matches()) {
                result.addError("Password is too common and easily guessable");
                break;
            }
        }

        // Dictionary word check
        String lowerPassword = password != null ? password.toLowerCase() : "";
        for (String word : DICTIONARY_WORDS) {
            if (lowerPassword.contains(word)) {
                result.addError("Password should not contain common dictionary words");
                break;
            }
        }

        // Personal info check (prevent using name/email)
        if (user != null) {
            if (password.toLowerCase().contains(user.getFirstName().toLowerCase()) ||
                password.toLowerCase().contains(user.getLastName().toLowerCase())) {
                result.addError("Password should not contain your name");
            }

            String emailPrefix = user.getEmail().split("@")[0];
            if (password.toLowerCase().contains(emailPrefix.toLowerCase())) {
                result.addError("Password should not contain parts of your email");
            }
        }

        // All whitespace check
        if (password != null && password.trim().isEmpty()) {
            result.addError("Password cannot be all whitespace");
        }

        // TODO: In production, integrate with HaveIBeenPwned API to check for breached passwords
        // checkAgainstBreachedPasswordDatabase(password);

        return result;
    }

    /**
     * Check if password meets minimum requirements for emergency override
     */
    public boolean meetsMinimumSecurity(String password) {
        return password != null && password.length() >= MIN_LENGTH;
    }

    /**
     * Calculate password strength score (0-100)
     */
    public int calculateStrength(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        int score = 0;

        // Length contribution (up to 40 points)
        score += Math.min(40, password.length() * 2);

        // Character variety (up to 30 points)
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));

        if (hasLower) score += 5;
        if (hasUpper) score += 5;
        if (hasDigit) score += 10;
        if (hasSpecial) score += 10;

        // Entropy (up to 30 points)
        long uniqueChars = password.chars().distinct().count();
        score += Math.min(30, (int) (uniqueChars * 2));

        return Math.min(100, score);
    }

    public static class PasswordValidationResult {
        private final java.util.List<String> errors = new java.util.ArrayList<>();

        public void addError(String error) {
            errors.add(error);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public java.util.List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }
}

