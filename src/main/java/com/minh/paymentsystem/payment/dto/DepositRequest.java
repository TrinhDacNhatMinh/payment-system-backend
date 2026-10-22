package com.minh.paymentsystem.payment.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.payment.json.DepositAmountDeserializer;

public record DepositRequest(
        @Schema(description = "Whole VND deposit amount from 10000 to 50000000 inclusive; fractional values are rejected without rounding", minimum = "10000", maximum = "50000000", multipleOf = 1, example = "500000")
        @NotNull(message = "Amount must not be null")
        @Positive(message = "Amount must be positive")
        @DecimalMin(value = "10000", message = "Amount must be at least 10000 VND")
        @DecimalMax(value = "50000000", message = "Amount must not exceed 50000000 VND")
        @Digits(integer = 16, fraction = 0, message = "Deposit amount must be a whole VND value")
        @JsonDeserialize(using = DepositAmountDeserializer.class)
        BigDecimal amount) {

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Deposit request only accepts amount");
    }
}
