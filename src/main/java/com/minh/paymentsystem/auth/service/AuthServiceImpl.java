package com.minh.paymentsystem.auth.service;

import java.time.Duration;
import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.AuthUserResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.auth.security.cookie.RefreshTokenCookieProvider;
import com.minh.paymentsystem.auth.security.jwt.JwtTokenProvider;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.dto.UserResponse;
import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.service.RefreshTokenService;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.wallet.service.WalletService;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;
    private final WalletService walletService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.debug("Registering new user");
        validateRegister(request);

        User user = buildRegisteredUser(request);
        userService.createUser(user);
        walletService.createWalletForUser(user);

        log.info("Registration processed: userId={}", user.getId());
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.debug("Processing login request");
        User user = authenticateUser(request);

        refreshTokenService.revokeAllUserTokens(user);

        AuthResponse response = issueAuthTokens(user);
        log.info("Login processed: userId={}", user.getId());
        return response;
    }

    @Override
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        log.debug("Processing refresh token request");
        RefreshToken token = validateRefreshToken(refreshToken);

        User user = token.getUser();
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        refreshTokenService.revokeAllUserTokens(user);

        AuthResponse response = issueAuthTokens(user);
        log.info("Token refresh processed: userId={}", user.getId());
        return response;
    }

    @Override
    @Transactional
    public String logout(String refreshToken) {
        log.debug("Processing logout request");
        if (!StringUtils.hasText(refreshToken)) {
            return refreshTokenCookieProvider.clearCookie();
        }
        refreshTokenService.findToken(refreshToken)
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    User user = token.getUser();
                    refreshTokenService.revokeAllUserTokens(user);
                    log.info("Logout processed: userId={}", user.getId());
                });
        return refreshTokenCookieProvider.clearCookie();
    }

    private void validateRegister(RegisterRequest request) {
        if (userService.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private User buildRegisteredUser(RegisterRequest request) {
        String encodedPassword = passwordEncoder.encode(request.password());

        return User.builder()
                .email(request.email())
                .password(encodedPassword)
                .fullName(request.fullName())
                .phone(request.phone())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private User authenticateUser(LoginRequest request) {
        User user;
        try {
            user = userService.getUserByEmail(request.email());
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, e);
            }
            throw e;
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        return user;
    }

    private RefreshToken validateRefreshToken(String refreshToken) {
        RefreshToken token = getRefreshToken(refreshToken);
        if (token.isRevoked()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return token;
    }

    private RefreshToken getRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING);
        }
        return refreshTokenService.getToken(refreshToken);
    }

    private AuthResponse issueAuthTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken();
        Duration refreshTokenMaxAge = Duration.ofMillis(jwtTokenProvider.getRefreshExpiration());
        saveRefreshToken(user, refreshToken, refreshTokenMaxAge);

        String cookie = refreshTokenCookieProvider.createCookie(refreshToken, refreshTokenMaxAge);
        return new AuthResponse(
                accessToken,
                "Bearer",
                jwtTokenProvider.getAccessExpiration() / 1000,
                AuthUserResponse.from(user),
                cookie);
    }

    private void saveRefreshToken(User user, String token, Duration maxAge) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(Instant.now().plus(maxAge))
                .revoked(false)
                .build();
        refreshTokenService.saveToken(refreshToken);
    }
}
