package com.minh.paymentsystem.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;

import java.time.LocalDateTime;

public record TransactionFilterRequest(
        @Schema(description = "Transaction type", example = "TRANSFER")
        TransactionType type,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Filter from date")
        LocalDateTime fromDate,

        @Schema(description = "Filter to date")
        LocalDateTime toDate,
        Integer page,
        Integer size
) {
    public TransactionFilterRequest {
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 10;
    }
}
