package com.minh.paymentsystem.transfer.dto;

public record TransferRequestError(
        int httpStatus,
        String code,
        String message) {
}
