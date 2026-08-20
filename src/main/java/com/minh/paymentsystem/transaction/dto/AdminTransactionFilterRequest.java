package com.minh.paymentsystem.transaction.dto;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;

import java.time.LocalDateTime;

public record AdminTransactionFilterRequest(
        Long userId,
        String email,
        TransactionType type,
        TransactionStatus status,
        LocalDateTime fromDate,
        LocalDateTime toDate
) {
}
