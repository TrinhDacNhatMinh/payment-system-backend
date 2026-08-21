package com.minh.paymentsystem.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DepositResponse(
        @Schema(description = "Unique order ID", example = "vnp_123456789")
        String orderId,

        @Schema(description = "URL to redirect user for payment", example = "https://sandbox.vnpayment.vn/...")
        String paymentUrl
) {
}
