package com.payops.identity.adapter.in.security;

import com.payops.shared.adapter.web.AuthenticatedUser;
import com.payops.shared.adapter.web.JsonErrorWriter;
import com.payops.shared.adapter.web.RateLimitExceededException;
import com.payops.shared.adapter.web.SlidingWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Throttles unauthenticated auth endpoints per client IP and the rest of the
 * API per authenticated user. Recovery and onboarding endpoints (which send
 * e-mail or redeem one-time tokens) get an additional, stricter IP budget.
 */
@RequiredArgsConstructor
class RateLimitFilter extends OncePerRequestFilter {

    static final String AUTH_CONFIG_PATH = "/api/v1/auth/config";
    private static final List<String> RECOVERY_PREFIXES = List.of(
            "/api/v1/auth/password/", "/api/v1/auth/invitations/", "/api/v1/auth/signup",
            "/api/v1/auth/verify-email");

    private final SlidingWindowRateLimiter authLimiter;
    private final SlidingWindowRateLimiter recoveryLimiter;
    private final SlidingWindowRateLimiter apiLimiter;
    private final JsonErrorWriter errorWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            if (path.startsWith("/api/v1/auth/") && !path.equals(AUTH_CONFIG_PATH)) {
                String key = "ip:" + request.getRemoteAddr();
                authLimiter.acquire(key);
                if (RECOVERY_PREFIXES.stream().anyMatch(path::startsWith)) {
                    recoveryLimiter.acquire(key);
                }
            } else if (path.startsWith("/api/") && !path.equals("/api/v1/stream")) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
                    apiLimiter.acquire("user:" + user.userId());
                }
            }
        } catch (RateLimitExceededException ex) {
            response.setHeader("Retry-After", String.valueOf(ex.retryAfterSeconds()));
            errorWriter.write(response, 429, "RATE_LIMITED", "Too many requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
