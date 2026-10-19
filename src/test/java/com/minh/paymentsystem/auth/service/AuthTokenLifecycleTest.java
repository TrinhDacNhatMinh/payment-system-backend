package com.minh.paymentsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.RefreshTokenRequest;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.RefreshTokenRepository;
import com.minh.paymentsystem.user.repository.UserRepository;

@SpringBootTest
@Transactional
class AuthTokenLifecycleTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldPersistRotatedTokenAndRejectReuseOfOldToken() {
        User user = createUser("rotation@test.com");
        AuthResponse login = authService.login(new LoginRequest(user.getEmail(), "password123"));
        RefreshToken otherToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token("another-active-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build());

        AuthResponse refreshed = authService.refresh(new RefreshTokenRequest(login.refreshToken()));
        refreshTokenRepository.flush();

        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(refreshTokenRepository.findByToken(login.refreshToken()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(otherToken.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findAllByUserAndRevokedFalse(user))
                .extracting(RefreshToken::getToken).containsExactly(refreshed.refreshToken());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh(new RefreshTokenRequest(login.refreshToken())));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED);
    }

    @Test
    void shouldRevokeAllRefreshTokensWhenLoggingOutWithExpiredToken() {
        User user = createUser("logout@test.com");
        AuthResponse login = authService.login(new LoginRequest(user.getEmail(), "password123"));
        RefreshToken expired = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token("expired-refresh-token")
                .expiresAt(Instant.now().minusSeconds(60))
                .revoked(false)
                .build());

        authService.logout(new RefreshTokenRequest(expired.getToken()));
        refreshTokenRepository.flush();

        assertThat(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(login.refreshToken()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(expired.getId()).orElseThrow().isRevoked()).isTrue();
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName("Test User")
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }
}
