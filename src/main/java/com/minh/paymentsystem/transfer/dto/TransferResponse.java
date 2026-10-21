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

        @Schema(description = "Whole VND balance after a new transfer, from 0 to 9999999999999999, without decimal zeros. Historical receipts may retain their exact fractional balance; no rounding is performed", type = "string", example = "0")
        @JsonSerialize(using = TransferAmountSerializer.class)
        BigDecimal fromWalletBalance,

        @Schema(description = "Whole VND amount from 1000 to 50000000 inclusive for new transfers; an immutable historical receipt may exceed this limit or retain its exact fractional amount", type = "string", example = "1000")
        @JsonSerialize(using = TransferAmountSerializer.class)
        BigDecimal amount,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Time of creation")
        LocalDateTime createdAt) {
}
