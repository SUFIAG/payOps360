package com.payops.identity.application.service;

import com.payops.identity.application.port.out.SecretCipher;
import com.payops.identity.domain.Totp;
import com.payops.identity.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.OptionalLong;

/**
 * Verifies a TOTP code (with replay protection) or a single-use recovery code.
 */
@Component
@RequiredArgsConstructor
class SecondFactorVerifier {

    enum Result { TOTP, RECOVERY_CODE, REJECTED }

    private final SecretCipher cipher;
    private final RecoveryCodes recoveryCodes;
    private final Clock clock;

    Result verify(User user, String code) {
        if (!user.mfaEnabled() || user.mfaSecretEncrypted() == null) {
            return Result.REJECTED;
        }
        String normalized = normalize(code);
        OptionalLong step = Totp.verify(cipher.decrypt(user.mfaSecretEncrypted()), normalized, clock.instant());
        if (step.isPresent()) {
            return user.acceptTotpStep(step.getAsLong()) ? Result.TOTP : Result.REJECTED;
        }
        return recoveryCodes.consume(user.id(), normalized) ? Result.RECOVERY_CODE : Result.REJECTED;
    }

    /** Verifies a code against a staged (not yet activated) secret during enrolment. */
    OptionalLong verifyStaged(User user, String code) {
        if (user.mfaSecretEncrypted() == null) {
            return OptionalLong.empty();
        }
        return Totp.verify(cipher.decrypt(user.mfaSecretEncrypted()), normalize(code), clock.instant());
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().replace(" ", "");
    }
}
