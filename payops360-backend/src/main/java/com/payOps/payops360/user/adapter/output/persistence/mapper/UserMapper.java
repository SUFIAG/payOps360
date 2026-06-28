package com.payops.payops360.user.adapter.output.persistence.mapper;

import com.payops.payops360.user.adapter.input.dto.UserResponse;
import com.payops.payops360.user.adapter.output.persistence.entity.UserEntity;
import com.payops.payops360.user.domain.model.User;
import org.springframework.stereotype.Component;

/**
 * User Mapper - Maps between User domain, entity, and DTOs
 */
@Component
public class UserMapper {

    /**
     * Convert Entity to Domain Model
     */
    public User toDomain(UserEntity entity) {
        if (entity == null) {
            return null;
        }

        return User.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganizationId())
                .email(entity.getEmail())
                .passwordHash(entity.getPasswordHash())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .status(entity.getStatus())
                .roles(entity.getRoles())
                .twoFactorEnabled(entity.getTwoFactorEnabled())
                .twoFactorSecret(entity.getTwoFactorSecret())
                .lastLoginAt(entity.getLastLoginAt())
                .lastLoginIp(entity.getLastLoginIp())
                .failedLoginAttempts(entity.getFailedLoginAttempts())
                .lockedUntil(entity.getLockedUntil())
                .passwordChangedAt(entity.getPasswordChangedAt())
                .passwordExpired(entity.getPasswordExpired())
                .mustChangePassword(entity.getMustChangePassword())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .invitedBy(entity.getInvitedBy())
                .invitationAcceptedAt(entity.getInvitationAcceptedAt())
                .build();
    }

    /**
     * Convert Domain Model to Entity
     */
    public UserEntity toEntity(User user) {
        if (user == null) {
            return null;
        }

        return UserEntity.builder()
                .id(user.getId())
                .organizationId(user.getOrganizationId())
                .email(user.getEmail())
                .passwordHash(user.getPasswordHash())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .status(user.getStatus())
                .roles(user.getRoles())
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .twoFactorSecret(user.getTwoFactorSecret())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lockedUntil(user.getLockedUntil())
                .passwordChangedAt(user.getPasswordChangedAt())
                .passwordExpired(user.isPasswordExpired())
                .mustChangePassword(user.isMustChangePassword())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .invitedBy(user.getInvitedBy())
                .invitationAcceptedAt(user.getInvitationAcceptedAt())
                .build();
    }

    /**
     * Convert Domain Model to Response DTO
     */
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .roles(user.getRoles())
                .organizationId(user.getOrganizationId())
                .twoFactorEnabled(user.isTwoFactorEnabled())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Convert Entity to Response DTO
     */
    public UserResponse toResponse(UserEntity entity) {
        return toResponse(toDomain(entity));
    }
}

