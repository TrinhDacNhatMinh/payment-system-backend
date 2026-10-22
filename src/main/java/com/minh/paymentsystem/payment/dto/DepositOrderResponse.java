package com.minh.paymentsystem.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record DepositOrderResponse(
        @Schema(description = "Opaque payment order identifier", example = "VNPAY_0123456789ABCDEF")
        String orderId,
        @Schema(description = "UUID v4 for new deposits; null for legacy orders")
        UUID idempotencyKey,
        @Schema(description = "Whole VND JSON number; new orders are between 10000 and 50000000", type = "number", example = "50000")
        BigDecimal amount,
        @Schema(example = "VND")
        String currency,
        PaymentOrderStatus status,
        @Schema(description = "Persisted creation time in Asia/Ho_Chi_Minh")
        LocalDateTime createdAt,
        @Schema(description = "Original link expiry in Asia/Ho_Chi_Minh; null for legacy orders")
        LocalDateTime paymentUrlExpiresAt,
        @Schema(description = "Verified gateway payment time; null until paid or for incomplete legacy metadata")
        LocalDateTime paidAt,
        @Schema(description = "True only for a pending order with an unexpired stored payment URL")
        boolean canContinuePayment,
        @Schema(description = "Original signed URL, returned only while canContinuePayment is true")
        String paymentUrl) {
}
