package com.minh.paymentsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshTokenRequest(
        @Schema(description = "The refresh token string", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
