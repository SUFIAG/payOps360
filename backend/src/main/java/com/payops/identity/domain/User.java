package com.payops.identity.domain;

import com.payops.shared.domain.DomainException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * User aggregate. Owns credential state, lockout state and MFA enrolment.
 */
public class User {

    private final Long id;
    private final UUID publicId;
    private final long organizationId;
    private final String email;
    private String fullName;
    private String passwordHash;
    private Role role;
    private UserStatus status;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private boolean mustChangePassword;
    private Instant passwordChangedAt;
    private boolean mfaEnabled;
    private String mfaSecretEncrypted;
    private Long mfaLastUsedStep;
    private Instant lastLoginAt;
    private String lastLoginIp;
    private final Instant createdAt;
    private final long version;

    @SuppressWarnings("java:S107")
    public User(Long id, UUID publicId, long organizationId, String email, String fullName, String passwordHash,
                Role role, UserStatus status, int failedLoginAttempts, Instant lockedUntil,
                boolean mustChangePassword, Instant passwordChangedAt, boolean mfaEnabled,
                String mfaSecretEncrypted, Long mfaLastUsedStep, Instant lastLoginAt, String lastLoginIp,
                Instant createdAt, long version) {
        this.id = id;
        this.publicId = publicId;
        this.organizationId = organizationId;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.mustChangePassword = mustChangePassword;
        this.passwordChangedAt = passwordChangedAt;
        this.mfaEnabled = mfaEnabled;
        this.mfaSecretEncrypted = mfaSecretEncrypted;
        this.mfaLastUsedStep = mfaLastUsedStep;
        this.lastLoginAt = lastLoginAt;
        this.lastLoginIp = lastLoginIp;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static User register(long organizationId, String email, String fullName, String passwordHash,
                                Role role, boolean mustChangePassword, Instant now) {
        return new User(null, UUID.randomUUID(), organizationId, email.trim().toLowerCase(), fullName.trim(),
                passwordHash, role, UserStatus.ACTIVE, 0, null, mustChangePassword, now, false, null, null,
                null, null, now, 0);
    }

    /**
     * An invited user cannot sign in until the invitation is accepted. The
     * password hash is an unusable placeholder (hash of a discarded random secret).
     */
    public static User invite(long organizationId, String email, String fullName, String placeholderHash,
                              Role role, Instant now) {
        return new User(null, UUID.randomUUID(), organizationId, email.trim().toLowerCase(), fullName.trim(),
                placeholderHash, role, UserStatus.INVITED, 0, null, false, now, false, null, null,
                null, null, now, 0);
    }

    /** Self-registered user who must confirm their e-mail address before signing in. */
    public static User registerUnverified(long organizationId, String email, String fullName, String passwordHash,
                                          Role role, Instant now) {
        return new User(null, UUID.randomUUID(), organizationId, email.trim().toLowerCase(), fullName.trim(),
                passwordHash, role, UserStatus.PENDING_VERIFICATION, 0, null, false, now, false, null, null,
                null, null, now, 0);
    }

    // ------------------------------------------------------------- onboarding

    public void acceptInvitation(String passwordHash, Instant now) {
        requireStatus(UserStatus.INVITED);
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = false;
        this.status = UserStatus.ACTIVE;
    }

    public void verifyEmail() {
        requireStatus(UserStatus.PENDING_VERIFICATION);
        this.status = UserStatus.ACTIVE;
    }

    /** The holder of a valid reset link chose a new password: clears any lockout. */
    public void completePasswordReset(String newPasswordHash, Instant now) {
        requireStatus(UserStatus.ACTIVE);
        changePassword(newPasswordHash, now);
        unlock();
    }

    public boolean isInvited() {
        return status == UserStatus.INVITED;
    }

    // ------------------------------------------------------------------ login

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean canAuthenticate(Instant now) {
        return status == UserStatus.ACTIVE && !isLocked(now);
    }

    /**
     * @return true when this failure caused the account to lock
     */
    public boolean registerFailedLogin(LockoutPolicy policy, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts % policy.maxFailedAttempts() == 0) {
            lockedUntil = now.plus(policy.lockDurationFor(failedLoginAttempts));
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(String ipAddress, Instant now, Duration passwordMaxAge) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
        lastLoginIp = ipAddress;
        if (passwordMaxAge != null && passwordChangedAt.plus(passwordMaxAge).isBefore(now)) {
            mustChangePassword = true;
        }
    }

    public void unlock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    // --------------------------------------------------------------- password

    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = false;
    }

    public void resetPassword(String temporaryPasswordHash, Instant now) {
        requireOnboarded();
        this.passwordHash = temporaryPasswordHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = true;
        unlock();
    }

    // -------------------------------------------------------------------- MFA

    public void stageMfaSecret(String encryptedSecret) {
        if (mfaEnabled) {
            throw new IllegalStateException("MFA is already enabled");
        }
        this.mfaSecretEncrypted = encryptedSecret;
    }

    public void activateMfa(long usedStep) {
        if (mfaSecretEncrypted == null) {
            throw new IllegalStateException("No MFA secret staged");
        }
        this.mfaEnabled = true;
        this.mfaLastUsedStep = usedStep;
    }

    /**
     * Replay protection: a TOTP time-step may be used only once.
     */
    public boolean acceptTotpStep(long step) {
        if (mfaLastUsedStep != null && step <= mfaLastUsedStep) {
            return false;
        }
        mfaLastUsedStep = step;
        return true;
    }

    public void disableMfa() {
        this.mfaEnabled = false;
        this.mfaSecretEncrypted = null;
        this.mfaLastUsedStep = null;
    }

    // ---------------------------------------------------------- administration

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public void disable() {
        requireOnboarded();
        this.status = UserStatus.DISABLED;
    }

    public void enable() {
        requireOnboarded();
        this.status = UserStatus.ACTIVE;
        unlock();
    }

    private void requireOnboarded() {
        if (status == UserStatus.INVITED || status == UserStatus.PENDING_VERIFICATION) {
            throw new DomainException("USER_NOT_ONBOARDED",
                    "This user has not completed onboarding yet; resend or revoke the invitation instead");
        }
    }

    private void requireStatus(UserStatus expected) {
        if (status != expected) {
            throw new DomainException("INVALID_USER_STATUS", "This action is not available for the account");
        }
    }

    public void rename(String fullName) {
        this.fullName = fullName.trim();
    }

    // ---------------------------------------------------------------- getters

    public Long id() { return id; }
    public UUID publicId() { return publicId; }
    public long organizationId() { return organizationId; }
    public String email() { return email; }
    public String fullName() { return fullName; }
    public String passwordHash() { return passwordHash; }
    public Role role() { return role; }
    public UserStatus status() { return status; }
    public int failedLoginAttempts() { return failedLoginAttempts; }
    public Instant lockedUntil() { return lockedUntil; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public Instant passwordChangedAt() { return passwordChangedAt; }
    public boolean mfaEnabled() { return mfaEnabled; }
    public String mfaSecretEncrypted() { return mfaSecretEncrypted; }
    public Long mfaLastUsedStep() { return mfaLastUsedStep; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public String lastLoginIp() { return lastLoginIp; }
    public Instant createdAt() { return createdAt; }
    public long version() { return version; }
}
