package com.minh.paymentsystem.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

public record RegisterRequest(
        @Schema(description = "User's email address", example = "newuser@example.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "User's password", example = "StrongPass@123")
        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        @Schema(description = "User's full name", example = "Nguyen Van A")
        @NotBlank(message = "Full name is required")
        String fullName,

        @Schema(description = "User's phone number", example = "0901234567")
        @NotBlank(message = "Phone number is required")
        String phone
) {}
