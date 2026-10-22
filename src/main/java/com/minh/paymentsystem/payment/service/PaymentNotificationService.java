package com.minh.paymentsystem.payment.service;

import java.math.BigDecimal;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.validation.WholeVnd;
import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.dto.VnpayNotification;
import com.minh.paymentsystem.payment.entity.PaymentOrder;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.repository.PaymentOrderRepository;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentNotificationService {

    private static final BigDecimal GATEWAY_MULTIPLIER = new BigDecimal("100");
    private static final Set<String> FINAL_FAILURE_CODES = Set.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79", "99");

    private final PaymentOrderRepository repository;
    private final WalletService walletService;
    private final TransactionService transactionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public VnpayCallbackResponse process(VnpayNotification notification) {
        PaymentOrder order = repository.findByOrderIdForUpdate(notification.orderId()).orElse(null);
        if (order == null) {
            return new VnpayCallbackResponse("01", "Order not found");
        }
        if (!matchesAmount(order, notification)) {
            return new VnpayCallbackResponse("04", "Invalid amount");
        }
        if (order.getStatus() != PaymentOrderStatus.PENDING) {
            return new VnpayCallbackResponse("02", "Order already confirmed");
        }
        boolean success = "00".equals(notification.responseCode()) && "00".equals(notification.transactionStatus());
        boolean failed = "02".equals(notification.transactionStatus()) && FINAL_FAILURE_CODES.contains(notification.responseCode());
        if (!success && !failed) {
            log.warn("VNPay result requires reconciliation: orderId={}", order.getOrderId());
            return new VnpayCallbackResponse("99", "Result requires reconciliation");
        }
        if (!WholeVnd.isWhole(order.getAmount())) {
            throw new BusinessException(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        }
        WholeVnd.requirePositiveAmount(order.getAmount());
        Wallet wallet = walletService.getWalletByUserId(order.getUser().getId());
        WholeVnd.requireWritableBalance(wallet.getBalance());
        if (success) {
            walletService.credit(wallet.getId(), order.getAmount());
        }
        transactionService.createTransaction(new CreateTransactionCommand(wallet, TransactionType.DEPOSIT,
                success ? TransactionStatus.SUCCESS : TransactionStatus.FAILED, order.getAmount(), order.getOrderId(),
                success ? "VNPay deposit successful" : "VNPay deposit failed"));
        order.setStatus(success ? PaymentOrderStatus.SUCCESS : PaymentOrderStatus.FAILED);
        order.setVnpTransactionNo(notification.transactionNo());
        if (success) {
            order.setPaidAt(notification.paidAt());
        }
        repository.saveAndFlush(order);
        log.info("Deposit notification processed: orderId={}, status={}", order.getOrderId(), order.getStatus());
        return new VnpayCallbackResponse("00", "Confirm Success");
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean matchesReturn(VnpayNotification notification) {
        return repository.findByOrderId(notification.orderId())
                .map(order -> matchesAmount(order, notification))
                .orElse(false);
    }

    private boolean matchesAmount(PaymentOrder order, VnpayNotification notification) {
        return order.getAmount().multiply(GATEWAY_MULTIPLIER).compareTo(notification.gatewayAmount()) == 0;
    }
}
