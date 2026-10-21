package com.minh.paymentsystem.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transfer.json.TransferAmountSerializer;

public record TransferResponse(
        @Schema(description = "ID of the transaction", example = "12345")
        Long transactionId,

        @Schema(description = "Exact balance after transfer, from 0 to 9999999999999999.99", type = "string", example = "0.00")
        @JsonSerialize(using = TransferAmountSerializer.class)
        BigDecimal fromWalletBalance,

        @Schema(description = "Exact amount of the processed transfer", type = "string", example = "1000.50")
        @JsonSerialize(using = TransferAmountSerializer.class)
        BigDecimal amount,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Time of creation")
        LocalDateTime createdAt) {
}
