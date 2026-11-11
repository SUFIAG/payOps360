package com.payops.identity.adapter.input.security;

import com.payops.identity.adapter.config.OnboardingProperties;
import com.payops.identity.adapter.config.SecurityProperties;
import com.payops.identity.adapter.output.security.JwtTokenService;
import com.payops.identity.application.port.input.SessionValidationUseCase;
import com.payops.shared.adapter.web.JsonErrorWriter;
import com.payops.shared.adapter.web.SlidingWindowRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Stateless API security:
 * <ul>
 *   <li>Bearer access tokens (15 min) + rotating refresh token in an
 *       HttpOnly, SameSite=Strict cookie scoped to /api/v1/auth.</li>
 *   <li>Cookie-authenticated endpoints additionally require the
 *       {@code X-Requested-With} header (CSRF defence: not settable cross-site
 *       without a CORS pre-flight that is restricted to known origins).</li>
 *   <li>Permission-based method security on every controller.</li>
 *   <li>Strict security headers, CORS allow-list, no server sessions.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig {

    private static final String[] PUBLIC_AUTH = {
            "/api/v1/auth/login",
            "/api/v1/auth/mfa/verify",
            "/api/v1/auth/mfa/enroll/start",
            "/api/v1/auth/mfa/enroll/confirm",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/invitations/accept",
            "/api/v1/auth/signup",
            "/api/v1/auth/verify-email",
            "/api/v1/auth/verify-email/resend"
    };
    private static final String[] PUBLIC_AUTH_READ = {
            RateLimitFilter.AUTH_CONFIG_PATH,
            "/api/v1/auth/invitations/*"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenService tokens,
                                            SessionValidationUseCase sessionValidation, JsonErrorWriter errorWriter,
                                            SecurityProperties properties, OnboardingProperties onboarding,
                                            Clock clock) throws Exception {
        var tokenFilter = new AccessTokenAuthenticationFilter(tokens, sessionValidation, errorWriter, clock);
        var rateLimitFilter = new RateLimitFilter(
                new SlidingWindowRateLimiter(properties.rateLimit().loginPerIpPerMinute(), Duration.ofMinutes(1), clock),
                new SlidingWindowRateLimiter(Math.max(1, onboarding.recoveryPerIpPerMinute()), Duration.ofMinutes(1), clock),
                new SlidingWindowRateLimiter(properties.rateLimit().apiPerUserPerMinute(), Duration.ofMinutes(1), clock),
                errorWriter);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(c -> { })
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(h -> h.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                request -> request.getRequestURI().startsWith("/api/v1/"),
                                new StaticHeadersWriter("Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), payment=()"))
                        .addHeaderWriter(new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                        .addHeaderWriter(new StaticHeadersWriter("Cache-Control", "no-store")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> errorWriter.write(res, 401, "UNAUTHENTICATED",
                                "Authentication is required"))
                        .accessDeniedHandler((req, res, ex) -> errorWriter.write(res, 403, "FORBIDDEN",
                                "You do not have permission to perform this action")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLIC_AUTH_READ).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").hasAuthority("USER_MANAGE")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, AccessTokenAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "X-Correlation-Id",
                "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
