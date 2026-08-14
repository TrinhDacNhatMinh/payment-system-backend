package com.minh.paymentsystem.auth.service.impl;

import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.LoginResponse;
import com.minh.paymentsystem.auth.dto.RefreshTokenRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.auth.security.JwtTokenProvider;
import com.minh.paymentsystem.auth.service.AuthService;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.dto.UserResponse;
import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.RefreshTokenRepository;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final WalletService walletService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = User.builder()
                .email(request.email())
                .password(encodedPassword)
                .fullName(request.fullName())
                .phone(request.phone())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        userRepository.save(user);
        walletService.createWalletForUser(user);

        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Processing login for email: {}", request.email());
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshTokenString = jwtTokenProvider.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenString)
                .expiresAt(Instant.now().plusMillis(jwtTokenProvider.getRefreshExpiration()))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return new LoginResponse(
                accessToken,
                refreshTokenString,
                "Bearer",
                jwtTokenProvider.getAccessExpiration() / 1000
        );
    }

    @Override
    @Transactional
    public LoginResponse refresh(RefreshTokenRequest request) {
        log.info("Processing refresh token request");
        RefreshToken token = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (token.isRevoked()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = token.getUser();
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole());

        return new LoginResponse(
                accessToken,
                token.getToken(),
                "Bearer",
                jwtTokenProvider.getAccessExpiration() / 1000
        );
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        log.info("Processing logout request");
        RefreshToken token = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }
}
