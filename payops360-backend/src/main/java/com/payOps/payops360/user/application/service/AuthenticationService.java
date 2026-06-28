package com.payops.payops360.user.application.service;

import com.payops.payops360.common.security.CurrentUserService;
import com.payops.payops360.common.security.JwtTokenProvider;
import com.payops.payops360.user.adapter.input.dto.*;
import com.payops.payops360.user.adapter.output.persistence.UserRepository;
import com.payops.payops360.user.adapter.output.persistence.entity.UserEntity;
import com.payops.payops360.user.adapter.output.persistence.mapper.UserMapper;
import com.payops.payops360.user.domain.model.Role;
import com.payops.payops360.user.domain.model.User;
import com.payops.payops360.user.domain.model.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication Service - Handles user authentication, registration, and token management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CurrentUserService currentUserService;

    /**
     * Register a new user
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Create user entity
        UserEntity userEntity = UserEntity.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .status(UserStatus.ACTIVE)
                .roles(Set.of(Role.ANALYST)) // Default role
                .organizationId(1L) // Default organization for MVP
                .twoFactorEnabled(false)
                .failedLoginAttempts(0)
                .passwordExpired(false)
                .mustChangePassword(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userEntity = userRepository.save(userEntity);
        log.info("User registered successfully: {}", userEntity.getEmail());

        return userMapper.toResponse(userEntity);
    }

    /**
     * Authenticate user and generate tokens
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getEmail());

        // Find user
        UserEntity userEntity = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check if account is locked
        if (userEntity.getLockedUntil() != null && LocalDateTime.now().isBefore(userEntity.getLockedUntil())) {
            throw new IllegalStateException("Account is temporarily locked. Please try again later.");
        }

        // Check if account is active
        if (userEntity.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), userEntity.getPasswordHash())) {
            // Increment failed login attempts
            userEntity.setFailedLoginAttempts(userEntity.getFailedLoginAttempts() + 1);
            if (userEntity.getFailedLoginAttempts() >= 5) {
                userEntity.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                log.warn("Account locked due to too many failed attempts: {}", userEntity.getEmail());
            }
            userRepository.save(userEntity);
            throw new BadCredentialsException("Invalid email or password");
        }

        // Reset failed login attempts on successful login
        userEntity.setFailedLoginAttempts(0);
        userEntity.setLockedUntil(null);
        userEntity.setLastLoginAt(LocalDateTime.now());
        userRepository.save(userEntity);

        // Create authentication
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userEntity.getEmail(),
                null,
                userEntity.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList())
        );

        // Generate tokens
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        log.info("User logged in successfully: {}", userEntity.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L) // 24 hours in seconds
                .user(userMapper.toResponse(userEntity))
                .build();
    }

    /**
     * Refresh access token
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Refreshing token");

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        // Extract user email from token
        String email = jwtTokenProvider.getUserEmailFromToken(refreshToken);

        // Find user
        UserEntity userEntity = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        // Check if user is still active
        if (userEntity.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }

        // Create authentication
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userEntity.getEmail(),
                null,
                userEntity.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toList())
        );

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        log.info("Token refreshed successfully for user: {}", email);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(userMapper.toResponse(userEntity))
                .build();
    }

    /**
     * Get current authenticated user
     */
    public UserResponse getCurrentUser() {
        String email = currentUserService.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalStateException("No authenticated user");
        }

        UserEntity userEntity = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        return userMapper.toResponse(userEntity);
    }

    /**
     * Change password
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        String email = currentUserService.getCurrentUserEmail();
        if (email == null) {
            throw new IllegalStateException("No authenticated user");
        }

        UserEntity userEntity = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), userEntity.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        // Update password
        userEntity.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userEntity.setPasswordChangedAt(LocalDateTime.now());
        userEntity.setMustChangePassword(false);
        userEntity.setUpdatedAt(LocalDateTime.now());

        userRepository.save(userEntity);
        log.info("Password changed successfully for user: {}", email);
    }

    /**
     * Logout (currently just for logging, JWT is stateless)
     */
    public void logout() {
        String email = currentUserService.getCurrentUserEmail();
        log.info("User logged out: {}", email);
        // In a production system, you might want to blacklist the token
    }
}

