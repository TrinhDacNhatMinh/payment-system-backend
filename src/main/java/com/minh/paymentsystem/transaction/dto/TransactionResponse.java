package com.minh.paymentsystem.transaction.dto;

import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        TransactionStatus status,
        String description,
        LocalDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
