package com.minh.paymentsystem.wallet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.wallet.entity.Wallet;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletResponse(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Filter by user ID", example = "1")
        Long userId,

        @Schema(description = "Wallet balance", example = "5000000")
        BigDecimal balance,

        @Schema(description = "Currency", example = "VND")
        String currency,

        @Schema(description = "Last updated time")
        LocalDateTime updatedAt
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUser().getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getUpdatedAt()
        );
    }
}
