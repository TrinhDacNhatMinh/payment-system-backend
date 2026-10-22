package com.minh.paymentsystem.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public interface PaymentGatewayService {
    String buildPaymentUrl(String orderId, BigDecimal amount, String ipAddress, LocalDateTime createdAt, LocalDateTime expiresAt);

    boolean verifyChecksum(Map<String, String> params);

    boolean matchesMerchant(Map<String, String> params);
}
