package com.stocksense.service.impl;

import com.stocksense.dto.request.LoginRequest;
import com.stocksense.dto.request.RefreshRequest;
import com.stocksense.dto.request.RegisterRequest;
import com.stocksense.dto.response.*;
import com.stocksense.entity.RefreshToken;
import com.stocksense.entity.User;
import com.stocksense.enums.AuditAction;
import com.stocksense.enums.UserRole;
import com.stocksense.exception.ConflictException;
import com.stocksense.exception.InvalidTokenException;
import com.stocksense.repository.RefreshTokenRepository;
import com.stocksense.repository.UserRepository;
import com.stocksense.service.AuditService;
import com.stocksense.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Transactional
    public AuthResponse register(RegisterRequest req, String ipAddress) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .email(req.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .role(UserRole.USER)
                .build();
        userRepository.save(user);

        auditService.log(user.getId(), AuditAction.USER_REGISTERED,
                Map.of("email", user.getEmail()), ipAddress);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String ipAddress) {
        // Spring Security validates credentials; throws AuthenticationException on failure
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );

        User user = userRepository.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new InvalidTokenException("User not found"));

        // Revoke all existing refresh tokens for this user (single-session policy)
        refreshTokenRepository.revokeAllForUser(user.getId());

        auditService.log(user.getId(), AuditAction.USER_LOGIN,
                Map.of("email", user.getEmail()), ipAddress);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        String tokenHash = jwtUtil.hashToken(req.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (!stored.isValid()) {
            throw new InvalidTokenException("Refresh token expired or revoked");
        }

        // Rotate: revoke old, issue new
        stored.setIsRevoked(true);
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        auditService.log(user.getId(), AuditAction.TOKEN_REFRESHED, Map.of(), null);

        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
        auditService.log(userId, AuditAction.USER_LOGOUT, Map.of(), null);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String rawRefresh   = jwtUtil.generateRefreshToken(user.getId());
        String refreshHash  = jwtUtil.hashToken(rawRefresh);

        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshHash)
                .expiresAt(OffsetDateTime.now().plusSeconds(jwtUtil.getRefreshTokenExpiryMs() / 1000))
                .build();
        refreshTokenRepository.save(rt);

        UserProfileResponse profile = new UserProfileResponse(
                user.getId(), user.getEmail(), user.getFullName(),
                user.getRole(), user.getCreatedAt()
        );

        return new AuthResponse(
                accessToken, rawRefresh, AuthResponse.TOKEN_TYPE,
                jwtUtil.getRefreshTokenExpiryMs(), profile   // Note: expiry is for refresh; access is 15m
        );
    }
}
