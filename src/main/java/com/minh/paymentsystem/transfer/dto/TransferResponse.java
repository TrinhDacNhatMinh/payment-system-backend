package com.minh.paymentsystem.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferResponse(
        @Schema(description = "ID of the transaction", example = "12345")
        Long transactionId,

        @Schema(description = "Remaining balance after transfer", example = "1500000")
        BigDecimal fromWalletBalance,

        @Schema(description = "Amount for the transaction", example = "500000")
        BigDecimal amount,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Time of creation")
        LocalDateTime createdAt
) {
}
