package com.minh.paymentsystem.payment.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;

public record DepositRequest(
        @Schema(description = "Whole VND deposit amount from 10000 to 50000000 inclusive; fractional values are rejected without rounding", minimum = "10000", maximum = "50000000", multipleOf = 1, example = "500000")
        @NotNull(message = "Amount must not be null")
        @Positive(message = "Amount must be positive")
        @Digits(integer = 16, fraction = 0, message = "Deposit amount must be a whole VND value")
        BigDecimal amount) {
}
