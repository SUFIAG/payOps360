package com.payops.identity.application.service;

import com.payops.audit.application.port.in.AuditTrail;
import com.payops.audit.domain.AuditEntry;
import com.payops.identity.application.port.out.ActionTokenRepository;
import com.payops.identity.application.port.out.OrganizationRepository;
import com.payops.identity.application.port.out.PasswordHasher;
import com.payops.identity.application.port.out.PasswordHistoryRepository;
import com.payops.identity.application.port.out.SessionRepository;
import com.payops.identity.application.port.out.UserRepository;
import com.payops.identity.domain.ActionToken;
import com.payops.identity.domain.LockoutPolicy;
import com.payops.identity.domain.Organization;
import com.payops.identity.domain.PasswordPolicy;
import com.payops.identity.domain.TokenPurpose;
import com.payops.identity.domain.User;
import com.payops.identity.domain.UserSession;
import com.payops.notification.application.port.in.EmailSender;
import com.payops.notification.domain.EmailMessage;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory adapters for identity application-service tests (no Spring, no database).
 */
final class IdentityFakes {

    static final Instant START = Instant.parse("2026-10-07T10:00:00Z");
    static final String STRONG_PASSWORD = "Harbour!Ledger7Quartz";
    private static final Pattern TOKEN_IN_LINK = Pattern.compile("token=([A-Za-z0-9_\\-%]+)");

    final MutableClock clock = new MutableClock(START);
    final Users users = new Users();
    final Organizations organizations = new Organizations();
    final Tokens tokenStore = new Tokens();
    final Hasher hasher = new Hasher();
    final History history = new History();
    final Sessions sessions = new Sessions();
    final List<EmailMessage> outbox = new ArrayList<>();
    final List<AuditEntry> audit = new ArrayList<>();

    final IdentitySettings identitySettings = new IdentitySettings(new LockoutPolicy(5, Duration.ofMinutes(15)),
            new PasswordPolicy(12, 5), Duration.ofDays(90), Duration.ofHours(12), 30, "PayOps360");
    final OneTimeTokens tokens = new OneTimeTokens(tokenStore, clock);
    final PasswordRules passwordRules = new PasswordRules(hasher, history, identitySettings);
    final EmailSender emailSender = outbox::add;
    final AuditTrail auditTrail = audit::add;

    IdentityEmails emails(OnboardingSettings settings) {
        return new IdentityEmails(emailSender, settings);
    }

    static OnboardingSettings onboarding(boolean selfSignup) {
        return new OnboardingSettings("http://localhost:3417/", selfSignup, true);
    }

    Organization organization(String name) {
        return organizations.save(new Organization(null, UUID.randomUUID(), name, Organization.slugify(name), false,
                true, "USD"));
    }

    /** Extracts the raw token from the link in the most recent e-mail to the recipient. */
    String tokenSentTo(String recipient) {
        EmailMessage message = outbox.stream().filter(m -> m.recipient().equals(recipient))
                .reduce((a, b) -> b).orElseThrow(() -> new AssertionError("no e-mail to " + recipient));
        Matcher matcher = TOKEN_IN_LINK.matcher(message.content().text());
        if (!matcher.find()) {
            throw new AssertionError("no token link in e-mail " + message.subject());
        }
        return java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }

    long emailsTo(String recipient) {
        return outbox.stream().filter(m -> m.recipient().equals(recipient)).count();
    }

    // ------------------------------------------------------------------ fakes

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static final class Users implements UserRepository {
        private final Map<Long, User> byId = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong(100);

        @Override
        public Optional<User> findByEmail(String email) {
            return byId.values().stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override
        public Optional<User> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<User> findByPublicId(long organizationId, UUID publicId) {
            return byId.values().stream()
                    .filter(u -> u.organizationId() == organizationId && u.publicId().equals(publicId)).findFirst();
        }

        @Override
        public PageResult<User> findByOrganization(long organizationId, PageQuery page) {
            List<User> items = byId.values().stream().filter(u -> u.organizationId() == organizationId).toList();
            return new PageResult<>(items, page.page(), page.size(), items.size());
        }

        @Override
        public boolean existsByEmail(String email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public User save(User u) {
            User stored = u.id() != null ? u : new User(ids.incrementAndGet(), u.publicId(), u.organizationId(),
                    u.email(), u.fullName(), u.passwordHash(), u.role(), u.status(), u.failedLoginAttempts(),
                    u.lockedUntil(), u.mustChangePassword(), u.passwordChangedAt(), u.mfaEnabled(),
                    u.mfaSecretEncrypted(), u.mfaLastUsedStep(), u.lastLoginAt(), u.lastLoginIp(), u.createdAt(),
                    u.version());
            byId.put(stored.id(), stored);
            return stored;
        }

        @Override
        public void delete(User user) {
            byId.remove(user.id());
        }
    }

    static final class Organizations implements OrganizationRepository {
        private final Map<Long, Organization> byId = new LinkedHashMap<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public Optional<Organization> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Organization> findAllActive() {
            return List.copyOf(byId.values());
        }

        @Override
        public Organization save(Organization o) {
            Organization stored = o.id() != null ? o : new Organization(ids.incrementAndGet(), o.publicId(), o.name(),
                    o.slug(), o.mfaRequired(), o.aiEnabled(), o.baseCurrency());
            byId.put(stored.id(), stored);
            return stored;
        }

        @Override
        public long count() {
            return byId.size();
        }

        @Override
        public boolean existsBySlug(String slug) {
            return byId.values().stream().anyMatch(o -> o.slug().equals(slug));
        }
    }

    static final class Tokens implements ActionTokenRepository {
        final List<ActionToken> rows = new ArrayList<>();
        private final AtomicLong ids = new AtomicLong();

        @Override
        public ActionToken save(ActionToken t) {
            ActionToken stored = new ActionToken(ids.incrementAndGet(), t.userId(), t.purpose(), t.tokenHash(),
                    t.expiresAt(), t.consumedAt(), t.createdAt());
            rows.add(stored);
            return stored;
        }

        @Override
        public Optional<ActionToken> findByHash(String tokenHash) {
            return rows.stream().filter(t -> t.tokenHash().equals(tokenHash)).findFirst();
        }

        @Override
        public boolean markConsumed(long tokenId, Instant now) {
            for (int i = 0; i < rows.size(); i++) {
                ActionToken t = rows.get(i);
                if (t.id() == tokenId && t.isUsable(now)) {
                    rows.set(i, new ActionToken(t.id(), t.userId(), t.purpose(), t.tokenHash(), t.expiresAt(), now,
                            t.createdAt()));
                    return true;
                }
            }
            return false;
        }

        @Override
        public int invalidateOutstanding(long userId, TokenPurpose purpose, Instant now) {
            int count = 0;
            for (int i = 0; i < rows.size(); i++) {
                ActionToken t = rows.get(i);
                if (t.userId() == userId && t.purpose() == purpose && t.isUsable(now)) {
                    rows.set(i, new ActionToken(t.id(), t.userId(), t.purpose(), t.tokenHash(), now, null,
                            t.createdAt()));
                    count++;
                }
            }
            return count;
        }

        @Override
        public Optional<Instant> lastIssuedAt(long userId, TokenPurpose purpose) {
            return rows.stream().filter(t -> t.userId() == userId && t.purpose() == purpose)
                    .map(ActionToken::createdAt).max(Comparator.naturalOrder());
        }
    }

    /** Transparent "hash" so tests stay fast; real BCrypt is covered elsewhere. */
    static final class Hasher implements PasswordHasher {
        int simulations;

        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String hash) {
            return hash != null && hash.equals("hashed:" + rawPassword);
        }

        @Override
        public void simulateVerification() {
            simulations++;
        }
    }

    static final class History implements PasswordHistoryRepository {
        final Map<Long, List<String>> hashes = new LinkedHashMap<>();

        @Override
        public List<String> recentHashes(long userId, int depth) {
            List<String> all = hashes.getOrDefault(userId, List.of());
            return all.subList(Math.max(0, all.size() - depth), all.size());
        }

        @Override
        public void add(long userId, String passwordHash) {
            hashes.computeIfAbsent(userId, k -> new ArrayList<>()).add(passwordHash);
        }
    }

    static final class Sessions implements SessionRepository {
        final List<Long> revokedForUsers = new ArrayList<>();

        @Override
        public UserSession save(UserSession session) {
            return session;
        }

        @Override
        public Optional<UserSession> findByRefreshTokenHash(String hash) {
            return Optional.empty();
        }

        @Override
        public Optional<UserSession> findByPublicId(UUID publicId) {
            return Optional.empty();
        }

        @Override
        public List<UserSession> findActiveByUser(long userId, Instant now) {
            return List.of();
        }

        @Override
        public int revokeFamily(UUID familyId, String reason, Instant now) {
            return 0;
        }

        @Override
        public int revokeAllForUser(long userId, String reason, Instant now) {
            revokedForUsers.add(userId);
            return 2;
        }

        @Override
        public boolean isActive(UUID publicId, Instant now) {
            return false;
        }
    }
}
