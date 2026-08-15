package com.minh.paymentsystem.transfer.dto;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        Long transactionId,
        BigDecimal fromWalletBalance,
        BigDecimal amount,
        TransactionStatus status,
        LocalDateTime createdAt
) {
}
