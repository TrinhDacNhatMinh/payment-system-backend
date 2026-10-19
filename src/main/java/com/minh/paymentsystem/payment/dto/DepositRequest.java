package com.minh.paymentsystem.payment.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;

public record DepositRequest(
        @Schema(description = "Deposit amount in VND, from 10000 to 50000000 inclusive", minimum = "10000", maximum = "50000000", example = "500000")
        @NotNull(message = "Amount must not be null")
        @Positive(message = "Amount must be positive")
        BigDecimal amount) {
}
