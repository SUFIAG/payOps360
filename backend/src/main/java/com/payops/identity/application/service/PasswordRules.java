package com.payops.identity.application.service;

import com.payops.identity.application.port.out.PasswordHasher;
import com.payops.identity.application.port.out.PasswordHistoryRepository;
import com.payops.identity.domain.User;
import com.payops.identity.domain.WeakPasswordException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Validates a new password for an existing account: strength policy plus
 * reuse of the current or one of the recent passwords.
 */
@Component
@RequiredArgsConstructor
class PasswordRules {

    private final PasswordHasher passwordHasher;
    private final PasswordHistoryRepository passwordHistory;
    private final IdentitySettings settings;

    void enforceNewPassword(User user, String newPassword) {
        settings.passwordPolicy().enforce(newPassword, user.email(), user.fullName());
        int depth = settings.passwordPolicy().historyDepth();
        List<String> previous = passwordHistory.recentHashes(user.id(), depth);
        if (passwordHasher.matches(newPassword, user.passwordHash())
                || previous.stream().anyMatch(hash -> passwordHasher.matches(newPassword, hash))) {
            throw new WeakPasswordException(List.of("Must not reuse one of your last " + depth + " passwords"));
        }
    }
}
