package com.minh.paymentsystem.payment.service;

import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.DepositResponse;
import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import java.util.Map;

public interface PaymentService {
    DepositResponse createDepositOrder(Long userId, DepositRequest request, String ipAddress);

    /**
     * IPN Webhook callback from VNPay.
     * Needs to be implemented with Idempotency logic and verifyChecksum.
     */
    VnpayCallbackResponse handleCallback(Map<String, String> params);

    /**
     * Return URL redirect for user.
     * Can optionally share logic with handleCallback or just verify and return status.
     */
    String processReturn(Map<String, String> params);
}
