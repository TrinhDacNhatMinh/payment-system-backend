package com.minh.paymentsystem.transaction.dto;

import java.time.LocalDateTime;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.common.constants.PaginationConstants;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;

public record TransactionFilterRequest(
        @Schema(description = "Transaction type", example = "TRANSFER_OUT")
        TransactionType type,

        @Schema(description = "Status of the transaction", example = "SUCCESS")
        TransactionStatus status,

        @Schema(description = "Inclusive lower bound for transaction creation time. ISO 8601 local date-time without an offset, compared directly with stored timestamps.", example = "2026-10-19T00:00:00")
        LocalDateTime fromDate,

        @Schema(description = "Inclusive upper bound for transaction creation time. ISO 8601 local date-time without an offset, compared directly with stored timestamps.", example = "2026-10-20T00:00:00")
        LocalDateTime toDate,

        @Schema(description = "Page number, starting at 0", defaultValue = "0", example = "0")
        @Min(0)
        Integer page,

        @Schema(description = "Number of items per page (1-100)", defaultValue = "10", example = "10")
        @Min(1)
        @Max(PaginationConstants.MAX_SIZE)
        Integer size) {
    public TransactionFilterRequest {
        if (page == null) {
            page = PaginationConstants.DEFAULT_PAGE;
        }

        if (size == null) {
            size = PaginationConstants.DEFAULT_SIZE;
        }
    }
}
