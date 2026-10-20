package com.minh.paymentsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.HttpCookie;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.AuthUserResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
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
        String loginRefreshToken = HttpCookie.parse(login.cookie()).get(0).getValue();
        RefreshToken otherToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token("another-active-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build());

        AuthResponse refreshed = authService.refresh(loginRefreshToken);
        String refreshedToken = HttpCookie.parse(refreshed.cookie()).get(0).getValue();
        refreshTokenRepository.flush();

        assertThat(login.user()).isEqualTo(new AuthUserResponse(user.getId(), user.getEmail(), Role.USER));
        assertThat(refreshed.user()).isEqualTo(login.user());
        assertThat(refreshedToken).isNotEqualTo(loginRefreshToken);
        assertThat(refreshTokenRepository.findByToken(loginRefreshToken).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(otherToken.getId()).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findAllByUserAndRevokedFalse(user))
                .extracting(RefreshToken::getToken).containsExactly(refreshedToken);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh(loginRefreshToken));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED);
    }

    @Test
    void shouldRevokeAllRefreshTokensWhenLoggingOutWithExpiredToken() {
        User user = createUser("logout@test.com");
        AuthResponse login = authService.login(new LoginRequest(user.getEmail(), "password123"));
        String loginRefreshToken = HttpCookie.parse(login.cookie()).get(0).getValue();
        RefreshToken expired = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token("expired-refresh-token")
                .expiresAt(Instant.now().minusSeconds(60))
                .revoked(false)
                .build());

        authService.logout(expired.getToken());
        refreshTokenRepository.flush();

        assertThat(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).isEmpty();
        assertThat(refreshTokenRepository.findByToken(loginRefreshToken).orElseThrow().isRevoked()).isTrue();
        assertThat(refreshTokenRepository.findById(expired.getId()).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void shouldKeepNewSessionActiveWhenLogoutWithOldRevokedTokenIsRepeated() {
        User user = createUser("repeat-logout@test.com");
        AuthResponse login = authService.login(new LoginRequest(user.getEmail(), "password123"));
        String oldToken = HttpCookie.parse(login.cookie()).get(0).getValue();
        String firstCookie = authService.logout(oldToken);
        AuthResponse newLogin = authService.login(new LoginRequest(user.getEmail(), "password123"));
        String newToken = HttpCookie.parse(newLogin.cookie()).get(0).getValue();

        String repeatedCookie = authService.logout(oldToken);
        refreshTokenRepository.flush();

        assertThat(HttpCookie.parse(firstCookie).get(0).getMaxAge()).isZero();
        assertThat(HttpCookie.parse(repeatedCookie).get(0).getMaxAge()).isZero();
        assertThat(refreshTokenRepository.findAllByUserAndRevokedFalse(user))
                .extracting(RefreshToken::getToken)
                .containsExactly(newToken);
    }

    @Test
    void shouldClearCookieWhenRefreshTokenHasBeenDeleted() {
        User user = createUser("deleted-token@test.com");
        AuthResponse login = authService.login(new LoginRequest(user.getEmail(), "password123"));
        String token = HttpCookie.parse(login.cookie()).get(0).getValue();
        refreshTokenRepository.delete(refreshTokenRepository.findByToken(token).orElseThrow());
        refreshTokenRepository.flush();

        String cookie = authService.logout(token);

        assertThat(HttpCookie.parse(cookie).get(0).getMaxAge()).isZero();
        assertThat(refreshTokenRepository.findAllByUserAndRevokedFalse(user)).isEmpty();
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
