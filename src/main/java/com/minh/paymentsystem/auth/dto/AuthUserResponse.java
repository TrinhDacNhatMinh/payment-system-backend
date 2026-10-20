package com.minh.paymentsystem.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;

public record AuthUserResponse(
        @Schema(description = "Authenticated user's ID", example = "1")
        Long id,

        @Schema(description = "Authenticated user's email address", example = "user@example.com")
        String email,

        @Schema(description = "Authenticated user's role", example = "USER")
        Role role) {

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole());
    }
}
