package com.minh.paymentsystem.payment.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.validation.WholeVnd;
import com.minh.paymentsystem.payment.dto.DepositOrderResponse;
import com.minh.paymentsystem.payment.entity.PaymentOrder;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.repository.PaymentOrderRepository;
import com.minh.paymentsystem.user.service.UserService;

@Service
@RequiredArgsConstructor
public class DepositOrderService {

    private static final int LINK_LIFETIME_MINUTES = 15;

    private final PaymentOrderRepository repository;
    private final UserService userService;
    private final PaymentGatewayService gateway;
    private final Clock paymentClock;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 5)
    public DepositOrderResponse createOrReplay(Long userId, UUID key, BigDecimal amount, String ipAddress) {
        PaymentOrder existing = repository.findByUserIdAndOperationAndIdempotencyKey(userId, PaymentOrder.DEPOSIT_OPERATION, key)
                .orElse(null);
        if (existing != null) {
            return replay(existing, amount);
        }
        LocalDateTime createdAt = LocalDateTime.now(paymentClock).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime expiresAt = createdAt.plusMinutes(LINK_LIFETIME_MINUTES);
        String orderId = "VNPAY_" + UUID.randomUUID().toString().replace("-", "");
        String url = gateway.buildPaymentUrl(orderId, amount, ipAddress, createdAt, expiresAt);
        PaymentOrder order = PaymentOrder.builder()
                .user(userService.getUserById(userId))
                .orderId(orderId)
                .operation(PaymentOrder.DEPOSIT_OPERATION)
                .idempotencyKey(key)
                .amount(amount)
                .status(PaymentOrderStatus.PENDING)
                .paymentUrl(url)
                .paymentUrlCreatedAt(createdAt)
                .paymentUrlExpiresAt(expiresAt)
                .createdAt(createdAt)
                .build();
        repository.saveAndFlush(order);
        return toResponse(order);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED, timeout = 5)
    public DepositOrderResponse replayAfterConflict(Long userId, UUID key, BigDecimal amount, DataIntegrityViolationException conflict) {
        PaymentOrder order = repository.findByUserIdAndOperationAndIdempotencyKey(userId, PaymentOrder.DEPOSIT_OPERATION, key)
                .orElseThrow(() -> conflict);
        return replay(order, amount);
    }

    @Transactional(readOnly = true)
    public DepositOrderResponse getOrder(Long userId, String orderId) {
        if (!PaymentReturnUrl.isOrderId(orderId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND);
        }
        return toResponse(repository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public DepositOrderResponse getRequest(Long userId, UUID key) {
        return toResponse(repository.findByUserIdAndOperationAndIdempotencyKey(userId, PaymentOrder.DEPOSIT_OPERATION, key)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEPOSIT_REQUEST_NOT_FOUND)));
    }

    private DepositOrderResponse replay(PaymentOrder order, BigDecimal amount) {
        if (order.getAmount().compareTo(amount) != 0) {
            throw new BusinessException(ErrorCode.DEPOSIT_KEY_REUSED);
        }
        return toResponse(order);
    }

    private DepositOrderResponse toResponse(PaymentOrder order) {
        if (!WholeVnd.isWhole(order.getAmount())) {
            throw new BusinessException(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        }
        WholeVnd.requirePositiveAmount(order.getAmount());
        boolean canContinue = order.getStatus() == PaymentOrderStatus.PENDING
                && order.getPaymentUrl() != null && !order.getPaymentUrl().isBlank()
                && order.getPaymentUrlCreatedAt() != null && order.getPaymentUrlExpiresAt() != null
                && order.getPaymentUrlExpiresAt().isAfter(LocalDateTime.now(paymentClock));
        return new DepositOrderResponse(order.getOrderId(), order.getIdempotencyKey(), order.getAmount().setScale(0),
                "VND", order.getStatus(), order.getPaymentUrlCreatedAt() == null ? order.getCreatedAt() : order.getPaymentUrlCreatedAt(),
                order.getPaymentUrlExpiresAt(), order.getPaidAt(), canContinue, canContinue ? order.getPaymentUrl() : null);
    }
}
