package com.minh.paymentsystem.transaction.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;

public record AdminTransactionFilterRequest(
        @Schema(description = "Filter by user ID", example = "1")
        Long userId,

        @Schema(description = "Filter by user email", example = "user@example.com")
        String email,

        @Schema(description = "Transaction type", example = "TRANSFER_OUT")
        TransactionType type,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Inclusive lower bound for transaction creation time. ISO 8601 local date-time without an offset, compared directly with stored timestamps.", example = "2026-10-19T00:00:00")
        LocalDateTime fromDate,

        @Schema(description = "Inclusive upper bound for transaction creation time. ISO 8601 local date-time without an offset, compared directly with stored timestamps.", example = "2026-10-20T00:00:00")
        LocalDateTime toDate) {
}
