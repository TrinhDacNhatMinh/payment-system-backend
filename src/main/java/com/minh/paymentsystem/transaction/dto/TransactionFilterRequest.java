package com.minh.paymentsystem.transaction.dto;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;

import java.time.LocalDateTime;

public record TransactionFilterRequest(
        TransactionType type,
        TransactionStatus status,
        LocalDateTime fromDate,
        LocalDateTime toDate,
        Integer page,
        Integer size
) {
    public TransactionFilterRequest {
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0) size = 10;
    }
}
