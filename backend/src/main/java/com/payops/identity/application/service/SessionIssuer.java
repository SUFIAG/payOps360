package com.payops.identity.application.service;

import com.payops.audit.application.port.input.AuditTrail;
import com.payops.audit.domain.AuditAction;
import com.payops.audit.domain.AuditEntry;
import com.payops.audit.domain.AuditOutcome;
import com.payops.identity.application.port.input.AuthOutcome;
import com.payops.identity.application.port.input.UserView;
import com.payops.identity.application.port.output.LoginAttemptRepository;
import com.payops.identity.application.port.output.OrganizationRepository;
import com.payops.identity.application.port.output.SessionRepository;
import com.payops.identity.application.port.output.TokenService;
import com.payops.identity.application.port.output.UserRepository;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserSession;
import com.payops.shared.application.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates a fully authenticated session once every factor has been verified.
 */
@Component
@RequiredArgsConstructor
class SessionIssuer {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final SessionRepository sessions;
    private final LoginAttemptRepository loginAttempts;
    private final TokenService tokens;
    private final AuditTrail auditTrail;
    private final IdentitySettings settings;
    private final Clock clock;

    AuthOutcome.Authenticated issue(User user, RequestContext context, List<String> recoveryCodes, String factor) {
        Instant now = clock.instant();
        user.registerSuccessfulLogin(context.ipAddress(), now, settings.passwordMaxAge());
        User saved = users.save(user);

        String refreshToken = tokens.newRefreshToken();
        UserSession session = sessions.save(UserSession.start(saved.id(), UUID.randomUUID(),
                tokens.hashOpaqueToken(refreshToken), context.ipAddress(), context.userAgent(), now,
                now.plus(settings.refreshTokenTtl())));
        TokenService.IssuedToken access = tokens.issueAccessToken(saved, session.publicId());

        loginAttempts.record(saved.email(), context.ipAddress(), context.userAgent(), true, factor, now);
        auditTrail.record(new AuditEntry(saved.organizationId(), saved.id(), saved.email(), AuditAction.LOGIN_SUCCESS,
                "SESSION", session.publicId().toString(), AuditOutcome.SUCCESS, context.ipAddress(),
                context.userAgent(), Map.of("factor", factor), now));

        return authenticated(saved, access, refreshToken, session.expiresAt(), recoveryCodes);
    }

    AuthOutcome.Authenticated rotate(User user, UserSession previous, RequestContext context) {
        Instant now = clock.instant();
        previous.revoke("ROTATED", now);
        sessions.save(previous);

        String refreshToken = tokens.newRefreshToken();
        // The family keeps its absolute expiry: rotation never extends a session indefinitely.
        UserSession next = sessions.save(UserSession.start(user.id(), previous.familyId(),
                tokens.hashOpaqueToken(refreshToken), context.ipAddress(), context.userAgent(), now,
                previous.expiresAt()));
        TokenService.IssuedToken access = tokens.issueAccessToken(user, next.publicId());
        return authenticated(user, access, refreshToken, next.expiresAt(), List.of());
    }

    private AuthOutcome.Authenticated authenticated(User user, TokenService.IssuedToken access, String refreshToken,
                                                    Instant refreshExpiry, List<String> recoveryCodes) {
        String orgName = organizations.findById(user.organizationId()).map(Organization::name).orElse("");
        return new AuthOutcome.Authenticated(access.value(), access.expiresAt(), refreshToken, refreshExpiry,
                UserView.of(user, clock.instant()), orgName, recoveryCodes);
    }
}
