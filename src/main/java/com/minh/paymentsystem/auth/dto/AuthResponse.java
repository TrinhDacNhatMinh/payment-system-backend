package com.minh.paymentsystem.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(description = "JWT Access Token for authentication", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
        String accessToken,

        @Schema(description = "Type of the token", example = "Bearer")
        String tokenType,

        @Schema(description = "Access token expiration time in seconds", example = "3600")
        long expiresIn,

        @Schema(description = "Authenticated user for this session")
        AuthUserResponse user,

        @Schema(hidden = true)
        @JsonIgnore
        String cookie) {
}
