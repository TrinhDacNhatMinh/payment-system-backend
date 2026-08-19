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
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import com.minh.paymentsystem.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    // Amount limits (10,000 VND - 50,000,000 VND)
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");
    private final UserRepository userRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletRepository walletRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final WalletService walletService;
    private final TransactionService transactionService;

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

    @Override
    @Transactional
    public VnpayCallbackResponse handleCallback(Map<String, String> params) {
        log.info("Received VNPay IPN webhook: {}", params);
        // 1. Verify checksum
        if (!paymentGatewayService.verifyChecksum(params)) {
            return new VnpayCallbackResponse("97", "Invalid Checksum");
        }

        // 2. Find PaymentOrder by vnp_TxnRef
        String orderId = params.get("vnp_TxnRef");
        PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));

        // 3. Idempotency check
        if (paymentOrder.getStatus() != PaymentOrderStatus.PENDING) {
            log.warn("Webhook Idempotency: Order {} already processed with status {}", orderId, paymentOrder.getStatus());
            return new VnpayCallbackResponse("02", "Order already confirmed");
        }

        String vnpResponseCode = params.get("vnp_ResponseCode");
        Wallet wallet = walletRepository.findByUserId(paymentOrder.getUser().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        if ("00".equals(vnpResponseCode)) {
            // 4. If vnp_ResponseCode == "00": success, credit wallet, create Transaction
            paymentOrder.setStatus(PaymentOrderStatus.SUCCESS);
            walletService.credit(wallet.getId(), paymentOrder.getAmount());

            transactionService.createTransaction(
                    wallet,
                    TransactionType.DEPOSIT,
                    TransactionStatus.SUCCESS,
                    paymentOrder.getAmount(),
                    orderId,
                    "VNPay deposit successful"
            );

        } else {
            // 5. If failure: update status to FAILED, do not credit wallet
            paymentOrder.setStatus(PaymentOrderStatus.FAILED);
            
            transactionService.createTransaction(
                    wallet,
                    TransactionType.DEPOSIT,
                    TransactionStatus.FAILED,
                    paymentOrder.getAmount(),
                    orderId,
                    "VNPay deposit failed"
            );
        }

        // Return VnpayCallbackResponse matching VNPay requirements ({"RspCode": "00", "Message": "Confirm Success"})
        return new VnpayCallbackResponse("00", "Confirm Success");
    }

    @Override
    public String processReturn(Map<String, String> params) {
        log.info("Received VNPay return redirect: {}", params);
        // 1. Verify checksum
        if (!paymentGatewayService.verifyChecksum(params)) {
            return "Invalid checksum. Payment verification failed.";
        }

        // 2. Find PaymentOrder
        String orderId = params.get("vnp_TxnRef");
        PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));

        // 3. Return success/fail message
        String vnpResponseCode = params.get("vnp_ResponseCode");
        if ("00".equals(vnpResponseCode)) {
            return "Payment successful for order: " + orderId;
        } else {
            return "Payment failed for order: " + orderId;
        }
    }
}
