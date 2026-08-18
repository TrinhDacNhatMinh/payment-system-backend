package com.minh.paymentsystem.payment.dto;

public record DepositResponse(
        String orderId,
        String paymentUrl
) {
}
