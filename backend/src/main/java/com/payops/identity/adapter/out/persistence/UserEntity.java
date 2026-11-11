package com.payops.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;
    private String email;
    @Column(name = "full_name")
    private String fullName;
    @Column(name = "password_hash")
    private String passwordHash;
    private String role;
    private String status;
    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "must_change_password")
    private boolean mustChangePassword;
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;
    @Column(name = "mfa_enabled")
    private boolean mfaEnabled;
    @Column(name = "mfa_secret_enc")
    private String mfaSecretEnc;
    @Column(name = "mfa_last_used_step")
    private Long mfaLastUsedStep;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "last_login_ip")
    private String lastLoginIp;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;
}
