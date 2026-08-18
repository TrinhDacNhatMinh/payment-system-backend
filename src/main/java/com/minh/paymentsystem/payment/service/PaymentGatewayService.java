package com.minh.paymentsystem.payment.service;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentGatewayService {
    String buildPaymentUrl(String orderId, BigDecimal amount, String ipAddress);
    boolean verifyChecksum(Map<String, String> params);
}
