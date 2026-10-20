package com.minh.paymentsystem.user.dto;

import java.time.Instant;

public record RefreshTokenState(
        Long userId,
        Instant expiresAt,
        boolean revoked) {
}
