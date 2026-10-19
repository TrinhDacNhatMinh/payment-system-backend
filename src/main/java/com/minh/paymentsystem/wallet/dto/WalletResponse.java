package com.minh.paymentsystem.wallet.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.wallet.entity.Wallet;

public record WalletResponse(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "ID of the user who owns this wallet", example = "1")
        Long userId,

        @Schema(description = "Wallet balance", example = "5000000")
        BigDecimal balance,

        @Schema(description = "Currency", example = "VND")
        String currency,

        @Schema(description = "Last updated time")
        LocalDateTime updatedAt) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getUpdatedAt());
    }
}
