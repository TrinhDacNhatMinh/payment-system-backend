package com.minh.paymentsystem.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;

import java.time.LocalDateTime;

public record AdminTransactionFilterRequest(
        @Schema(description = "Filter by user ID", example = "1")
        Long userId,

        @Schema(description = "Filter by user email", example = "user@example.com")
        String email,

        @Schema(description = "Transaction type", example = "TRANSFER")
        TransactionType type,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Filter from date")
        LocalDateTime fromDate,

        @Schema(description = "Filter to date")
        LocalDateTime toDate
) {
}
