package com.minh.paymentsystem.transaction.dto;

import java.math.BigDecimal;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.wallet.entity.Wallet;

public record CreateTransactionCommand(
        Wallet wallet,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String referenceCode,
        String description) {
}
