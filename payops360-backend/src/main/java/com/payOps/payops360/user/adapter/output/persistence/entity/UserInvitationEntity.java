package com.payops.payops360.user.adapter.output.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * JPA Entity for User Invitations (Invite-only registration)
 */
@Entity
@Table(name = "user_invitations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // Invitation details
    @Column(nullable = false)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String roles; // JSON array of roles

    // Security
    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean used = false;

    // Metadata
    @Column(name = "invited_by", nullable = false)
    private String invitedBy;

    @Column(name = "invited_at", nullable = false, updatable = false)
    private LocalDateTime invitedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_from_ip", length = 45)
    private String acceptedFromIp;

    @PrePersist
    protected void onCreate() {
        invitedAt = LocalDateTime.now();
    }
}

