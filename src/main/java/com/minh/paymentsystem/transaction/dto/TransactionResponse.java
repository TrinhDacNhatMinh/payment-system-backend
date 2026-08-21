package com.minh.paymentsystem.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record TransactionResponse(
        @Schema(description = "ID", example = "1")
        Long id,

        @Schema(description = "Transaction type", example = "TRANSFER")
        TransactionType type,

        @Schema(description = "Amount for the transaction", example = "500000")
        BigDecimal amount,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Transaction description", example = "Transfer for lunch")
        String description,

        @Schema(description = "Time of creation")
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
