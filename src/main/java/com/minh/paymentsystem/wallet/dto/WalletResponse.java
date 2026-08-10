package com.minh.paymentsystem.wallet.dto;

import com.minh.paymentsystem.wallet.entity.Wallet;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletResponse(
        Long id,
        Long userId,
        BigDecimal balance,
        String currency,
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
