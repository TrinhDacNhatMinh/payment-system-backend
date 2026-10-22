package com.minh.paymentsystem.payment.service;

import java.net.URI;
import java.util.List;

import org.springframework.util.MultiValueMap;

import com.minh.paymentsystem.payment.dto.DepositOrderResponse;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;

public interface PaymentService {
    DepositOrderResponse createDepositOrder(Long userId, List<String> keys, DepositRequest request, String ipAddress);

    DepositOrderResponse getOrder(Long userId, String orderId);

    DepositOrderResponse getDepositRequest(Long userId, String key);

    /**
     * IPN Webhook callback from VNPay.
     * Validates the notification and commits order, wallet and transaction atomically.
     */
    VnpayCallbackResponse handleCallback(MultiValueMap<String, String> params);

    /**
     * Return URL redirect for user.
     * Verifies return data without changing payment state or wallet balance.
     */
    URI processReturn(MultiValueMap<String, String> params);
}
