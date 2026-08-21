package com.minh.paymentsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;

public record UserResponse(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Filter by user email", example = "user@example.com")
        String email,

        @Schema(description = "Full name", example = "Nguyen Van A")
        String fullName,

        @Schema(description = "User role", example = "USER")
        Role role,

        @Schema(description = "User status", example = "ACTIVE")
        UserStatus status
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getStatus()
        );
    }
}
