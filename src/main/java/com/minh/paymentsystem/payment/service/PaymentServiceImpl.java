package com.minh.paymentsystem.payment.service;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.payment.dto.DepositOrderResponse;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.dto.VnpayNotification;
import com.minh.paymentsystem.transfer.security.IdempotencyKey;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final DepositOrderService depositOrderService;
    private final PaymentNotificationService notificationService;
    private final PaymentGatewayService gateway;
    private final PaymentReturnUrl returnUrl;
    private final Validator validator;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DepositOrderResponse createDepositOrder(Long userId, List<String> keys, DepositRequest request, String ipAddress) {
        UUID key = IdempotencyKey.parse(keys);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            return depositOrderService.createOrReplay(userId, key, request.amount(), ipAddress);
        } catch (DataIntegrityViolationException exception) {
            // The failed insert has rolled back before reading the unique-key winner.
            return depositOrderService.replayAfterConflict(userId, key, request.amount(), exception);
        } catch (PessimisticLockingFailureException | QueryTimeoutException | TransactionTimedOutException exception) {
            throw new BusinessException(ErrorCode.DEPOSIT_REQUEST_IN_PROGRESS, exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DepositOrderResponse getOrder(Long userId, String orderId) {
        return depositOrderService.getOrder(userId, orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public DepositOrderResponse getDepositRequest(Long userId, String key) {
        return depositOrderService.getRequest(userId, IdempotencyKey.parse(Collections.singletonList(key)));
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VnpayCallbackResponse handleCallback(MultiValueMap<String, String> values) {
        try {
            Map<String, String> params = singleParameters(values);
            if (params == null) {
                return new VnpayCallbackResponse("99", "Invalid parameters");
            }
            if (!gateway.verifyChecksum(params)) {
                return new VnpayCallbackResponse("97", "Invalid Checksum");
            }
            if (!gateway.matchesMerchant(params)) {
                return new VnpayCallbackResponse("99", "Invalid merchant");
            }
            VnpayNotification notification = VnpayNotification.parse(params).orElse(null);
            if (notification == null) {
                return new VnpayCallbackResponse("99", "Invalid parameters");
            }
            // The worker proxy commits before returning, or rolls back before an exception reaches this catch.
            return notificationService.process(notification);
        } catch (RuntimeException exception) {
            log.warn("VNPay notification could not be committed: exceptionType={}", exception.getClass().getSimpleName());
            return new VnpayCallbackResponse("99", "Unable to process notification");
        }
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public URI processReturn(MultiValueMap<String, String> values) {
        try {
            Map<String, String> params = singleParameters(values);
            if (params == null || !gateway.verifyChecksum(params) || !gateway.matchesMerchant(params)) {
                return returnUrl.invalid();
            }
            VnpayNotification notification = VnpayNotification.parse(params).orElse(null);
            return notification != null && notificationService.matchesReturn(notification)
                    ? returnUrl.forVerifiedReturn(notification.orderId(), params)
                    : returnUrl.invalid();
        } catch (RuntimeException exception) {
            log.warn("VNPay return could not be verified: exceptionType={}", exception.getClass().getSimpleName());
            return returnUrl.unavailable();
        }
    }

    private Map<String, String> singleParameters(MultiValueMap<String, String> values) {
        if (values == null || values.isEmpty() || values.values().stream()
                .anyMatch(entries -> entries == null || entries.size() != 1 || entries.get(0) == null)) {
            return null;
        }
        return values.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get(0)));
    }
}
