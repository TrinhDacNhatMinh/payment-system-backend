package com.minh.paymentsystem.payment.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.validation.WholeVnd;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.DepositResponse;
import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.entity.PaymentOrder;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.repository.PaymentOrderRepository;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    // Amount limits (10,000 VND - 50,000,000 VND)
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("10000");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000000");
    private final UserService userService;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final WalletService walletService;
    private final TransactionService transactionService;

    @Override
    @Transactional
    public DepositResponse createDepositOrder(Long userId, DepositRequest request, String ipAddress) {
        log.debug("Creating deposit order for user id={} with amount={}", userId, request.amount());

        validateDepositAmount(request.amount());
        User user = userService.getUserById(userId);

        String orderId = "VNPAY_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        savePendingOrder(user, orderId, request.amount());
        String paymentUrl = paymentGatewayService.buildPaymentUrl(orderId, request.amount(), ipAddress);

        log.info("Deposit order prepared: orderId={}, userId={}", orderId, userId);
        return new DepositResponse(orderId, paymentUrl);
    }

    @Override
    @Transactional
    public VnpayCallbackResponse handleCallback(Map<String, String> params) {
        log.debug("Received VNPay IPN webhook");
        if (!paymentGatewayService.verifyChecksum(params)) {
            return new VnpayCallbackResponse("97", "Invalid Checksum");
        }

        String orderId = params.get("vnp_TxnRef");
        PaymentOrder paymentOrder = getPaymentOrder(orderId);

        log.debug("Verified VNPay IPN webhook for order={}", paymentOrder.getOrderId());

        if (paymentOrder.getStatus() != PaymentOrderStatus.PENDING) {
            log.info("Webhook Idempotency: Order {} already processed with status {}", paymentOrder.getOrderId(), paymentOrder.getStatus());
            return new VnpayCallbackResponse("02", "Order already confirmed");
        }

        if (!WholeVnd.isWhole(paymentOrder.getAmount())) {
            throw new BusinessException(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        }
        WholeVnd.requirePositiveAmount(paymentOrder.getAmount());

        String vnpResponseCode = params.get("vnp_ResponseCode");
        Wallet wallet = walletService.getWalletByUserId(paymentOrder.getUser().getId());

        if ("00".equals(vnpResponseCode)) {
            completeDeposit(paymentOrder, wallet, orderId);
        } else {
            failDeposit(paymentOrder, wallet, orderId);
        }

        log.info("Deposit callback processed: orderId={}, status={}", paymentOrder.getOrderId(), paymentOrder.getStatus());

        // Return VnpayCallbackResponse matching VNPay requirements ({"RspCode": "00", "Message": "Confirm Success"})
        return new VnpayCallbackResponse("00", "Confirm Success");
    }

    @Override
    @Transactional(readOnly = true)
    public String processReturn(Map<String, String> params) {
        log.debug("Received VNPay return redirect");
        if (!paymentGatewayService.verifyChecksum(params)) {
            return "Invalid checksum. Payment verification failed.";
        }

        String orderId = params.get("vnp_TxnRef");
        PaymentOrder paymentOrder = getPaymentOrder(orderId);

        log.debug("Verified VNPay return redirect for order={}", paymentOrder.getOrderId());

        String vnpResponseCode = params.get("vnp_ResponseCode");
        if ("00".equals(vnpResponseCode)) {
            return "Payment successful for order: " + orderId;
        } else {
            return "Payment failed for order: " + orderId;
        }
    }

    private void validateDepositAmount(BigDecimal amount) {
        WholeVnd.requirePositiveAmount(amount);
        if (amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }
    }

    private void savePendingOrder(User user, String orderId, BigDecimal amount) {
        PaymentOrder paymentOrder = PaymentOrder.builder()
                .user(user)
                .orderId(orderId)
                .amount(amount)
                .status(PaymentOrderStatus.PENDING)
                .build();
        paymentOrderRepository.save(paymentOrder);
    }

    private PaymentOrder getPaymentOrder(String orderId) {
        return paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
    }

    private void completeDeposit(PaymentOrder paymentOrder, Wallet wallet, String orderId) {
        paymentOrder.setStatus(PaymentOrderStatus.SUCCESS);
        walletService.credit(wallet.getId(), paymentOrder.getAmount());
        transactionService.createTransaction(new CreateTransactionCommand(
                wallet,
                TransactionType.DEPOSIT,
                TransactionStatus.SUCCESS,
                paymentOrder.getAmount(),
                orderId,
                "VNPay deposit successful"));
    }

    private void failDeposit(PaymentOrder paymentOrder, Wallet wallet, String orderId) {
        paymentOrder.setStatus(PaymentOrderStatus.FAILED);
        transactionService.createTransaction(new CreateTransactionCommand(
                wallet,
                TransactionType.DEPOSIT,
                TransactionStatus.FAILED,
                paymentOrder.getAmount(),
                orderId,
                "VNPay deposit failed"));
    }
}
