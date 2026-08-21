package com.minh.paymentsystem.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferRequest(
        @Schema(description = "Recipient email", example = "recipient@example.com")
        @NotBlank(message = "Recipient email must not be blank")
        String toEmail,

        @Schema(description = "Amount for the transaction", example = "500000")
        @NotNull(message = "Amount must not be null")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @Schema(description = "Transaction description", example = "Transfer for lunch")
        String description
) {
}
