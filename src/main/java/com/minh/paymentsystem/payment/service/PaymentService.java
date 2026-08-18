package com.minh.paymentsystem.payment.service;

import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.DepositResponse;

public interface PaymentService {
    DepositResponse createDepositOrder(Long userId, DepositRequest request, String ipAddress);
}
