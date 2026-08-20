package com.minh.paymentsystem.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request to lock a user account")
public record LockUserRequest(
        @Schema(description = "Reason for locking the account (optional)", example = "Suspicious activity detected")
        String reason
) {
}
