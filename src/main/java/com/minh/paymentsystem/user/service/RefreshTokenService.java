package com.minh.paymentsystem.user.service;

import java.util.Optional;

import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.User;

public interface RefreshTokenService {
    void saveToken(RefreshToken token);

    void revokeAllUserTokens(User user);

    RefreshToken getToken(String token);

    Optional<RefreshToken> findToken(String token);

    void cleanUpTokens(java.time.Instant threshold);
}
