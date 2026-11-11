package com.payops.identity.adapter.input.security;

import com.payops.identity.adapter.output.security.JwtTokenService;
import com.payops.identity.application.port.input.SessionValidationUseCase;
import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.JsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates Bearer access tokens. Besides signature/expiry/type checks,
 * the referenced server-side session must still be active, so logout,
 * password change and admin revocation take effect within seconds.
 */
@RequiredArgsConstructor
class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final long SESSION_CACHE_MILLIS = 10_000;
    private static final String BEARER = "Bearer ";
    /** Paths reachable while a password change is pending. */
    private static final Set<String> PASSWORD_CHANGE_ALLOWED = Set.of(
            "/api/v1/account/me", "/api/v1/account/password", "/api/v1/auth/logout");

    private final JwtTokenService tokens;
    private final SessionValidationUseCase sessionValidation;
    private final JsonErrorWriter errorWriter;
    private final Clock clock;
    private final Map<UUID, CachedValidity> sessionCache = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        var claims = tokens.parseAccessToken(header.substring(BEARER.length()).trim()).orElse(null);
        if (claims == null || !sessionActive(claims.sessionId())) {
            errorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "TOKEN_INVALID",
                    "Your session is invalid or has expired");
            return;
        }
        if (claims.passwordChangeRequired() && !PASSWORD_CHANGE_ALLOWED.contains(request.getRequestURI())) {
            errorWriter.write(response, HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
                    "You must change your password before continuing");
            return;
        }

        AuthenticatedUser principal = new AuthenticatedUser(claims.userId(), claims.organizationId(), claims.email(),
                claims.role(), claims.permissions(), claims.sessionId());
        List<SimpleGrantedAuthority> authorities = claims.permissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean sessionActive(UUID sessionId) {
        long now = clock.millis();
        CachedValidity cached = sessionCache.get(sessionId);
        if (cached != null && now - cached.checkedAt() < SESSION_CACHE_MILLIS) {
            return cached.active();
        }
        boolean active = sessionValidation.isSessionActive(sessionId);
        if (sessionCache.size() > 50_000) {
            sessionCache.clear();
        }
        sessionCache.put(sessionId, new CachedValidity(active, now));
        return active;
    }

    private record CachedValidity(boolean active, long checkedAt) {
    }
}
