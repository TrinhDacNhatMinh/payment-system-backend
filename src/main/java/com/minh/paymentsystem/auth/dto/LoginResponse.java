package com.minh.paymentsystem.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "JWT Access Token for authentication", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Refresh Token to obtain new access token", example = "d290f1ee-6c54-4b01-90e6-d701748f0851")
        String refreshToken,

        @Schema(description = "Type of the token", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token expiration time in milliseconds", example = "3600000")
        long expiresIn
) {}
