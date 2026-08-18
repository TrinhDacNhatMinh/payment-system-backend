package com.minh.paymentsystem.payment.service.impl;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.DepositResponse;
import com.minh.paymentsystem.payment.entity.PaymentOrder;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.repository.PaymentOrderRepository;
import com.minh.paymentsystem.payment.service.PaymentGatewayService;
import com.minh.paymentsystem.payment.service.PaymentService;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final UserRepository userRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayService paymentGatewayService;

    // Amount limits (10,000 VND - 50,000,000 VND)
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");

    @Override
    @Transactional
    public DepositResponse createDepositOrder(Long userId, DepositRequest request, String ipAddress) {
        log.info("Creating deposit order for user id={} with amount={}", userId, request.amount());

        // 1. Validate amount
        if (request.amount().compareTo(MIN_AMOUNT) < 0 || request.amount().compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));

        // 2. Generate unique orderId
        String orderId = "VNPAY_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        // 3. Create PaymentOrder with status PENDING
        PaymentOrder paymentOrder = PaymentOrder.builder()
                .user(user)
                .orderId(orderId)
                .amount(request.amount())
                .status(PaymentOrderStatus.PENDING)
                .build();

        paymentOrderRepository.save(paymentOrder);

        // 4. Call paymentGatewayService.buildPaymentUrl()
        String paymentUrl = paymentGatewayService.buildPaymentUrl(orderId, request.amount(), ipAddress);

        // 5. Return DepositResponse
        return new DepositResponse(orderId, paymentUrl);
    }
}
