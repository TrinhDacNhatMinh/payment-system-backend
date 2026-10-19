package com.minh.paymentsystem.auth.service;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.RefreshTokenRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.auth.security.JwtTokenProvider;
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
    public AuthResponse refresh(RefreshTokenRequest request) {
        log.debug("Processing refresh token request");
        RefreshToken token = validateRefreshToken(request);

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
    public void logout(RefreshTokenRequest request) {
        log.debug("Processing logout request");
        RefreshToken token = refreshTokenService.getToken(request.refreshToken());
        if (token.isRevoked()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }
        User user = token.getUser();
        refreshTokenService.revokeAllUserTokens(user);
        log.info("Logout processed: userId={}", user.getId());
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

    private RefreshToken validateRefreshToken(RefreshTokenRequest request) {
        RefreshToken token = refreshTokenService.getToken(request.refreshToken());
        if (token.isRevoked()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        return token;
    }

    private AuthResponse issueAuthTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        String refreshToken = jwtTokenProvider.generateRefreshToken();
        saveRefreshToken(user, refreshToken);

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtTokenProvider.getAccessExpiration() / 1000);
    }

    private void saveRefreshToken(User user, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshExpiration()))
                .revoked(false)
                .build();
        refreshTokenService.saveToken(refreshToken);
    }
}
