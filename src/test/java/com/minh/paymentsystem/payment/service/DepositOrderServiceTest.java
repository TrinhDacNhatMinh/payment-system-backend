package com.minh.paymentsystem.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.payment.dto.DepositOrderResponse;
import com.minh.paymentsystem.payment.entity.PaymentOrder;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.repository.PaymentOrderRepository;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.service.UserService;

class DepositOrderServiceTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 22, 12, 0);
    private final PaymentOrderRepository repository = mock(PaymentOrderRepository.class);
    private final UserService users = mock(UserService.class);
    private final PaymentGatewayService gateway = mock(PaymentGatewayService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private final DepositOrderService service = new DepositOrderService(repository, users, gateway, clock);
    private PaymentOrder order;

    @BeforeEach
    void setUp() {
        order = PaymentOrder.builder().user(User.builder().id(1L).build()).orderId("ORDER_1")
                .operation(PaymentOrder.DEPOSIT_OPERATION).idempotencyKey(KEY).amount(new BigDecimal("10000.00"))
                .status(PaymentOrderStatus.PENDING).paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?signed=original")
                .paymentUrlCreatedAt(NOW).paymentUrlExpiresAt(NOW.plusMinutes(15)).createdAt(NOW).build();
    }

    @Test
    void shouldStoreOrderKeySignedUrlAndExactExpiryTogether() {
        when(users.getUserById(1L)).thenReturn(order.getUser());
        when(gateway.buildPaymentUrl(anyString(), any(), anyString(), eq(NOW), eq(NOW.plusMinutes(15))))
                .thenReturn(order.getPaymentUrl());
        DepositOrderResponse response = service.createOrReplay(1L, KEY, new BigDecimal("10000"), "127.0.0.1");
        ArgumentCaptor<PaymentOrder> saved = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo(KEY);
        assertThat(response.paymentUrl()).isEqualTo(order.getPaymentUrl());
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.paymentUrlExpiresAt()).isEqualTo(NOW.plusMinutes(15));
        assertThat(response.canContinuePayment()).isTrue();
    }

    @Test
    void shouldReplaySameOrderWithoutSigningAgainOrExtendingExpiry() {
        when(repository.findByUserIdAndOperationAndIdempotencyKey(1L, PaymentOrder.DEPOSIT_OPERATION, KEY)).thenReturn(Optional.of(order));
        DepositOrderResponse response = service.createOrReplay(1L, KEY, new BigDecimal("10000"), "other-ip");
        assertThat(response.orderId()).isEqualTo("ORDER_1");
        assertThat(response.paymentUrl()).isEqualTo(order.getPaymentUrl());
        assertThat(response.paymentUrlExpiresAt()).isEqualTo(NOW.plusMinutes(15));
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(gateway, users);
    }

    @Test
    void shouldRejectKeyReusedWithDifferentAmount() {
        when(repository.findByUserIdAndOperationAndIdempotencyKey(1L, PaymentOrder.DEPOSIT_OPERATION, KEY)).thenReturn(Optional.of(order));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createOrReplay(1L, KEY, new BigDecimal("20000"), "127.0.0.1"));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEPOSIT_KEY_REUSED);
        verifyNoInteractions(gateway);
    }

    @Test
    void shouldHideExpiredLinkWithoutFailingOrRegeneratingOrder() {
        order.setPaymentUrlExpiresAt(NOW);
        when(repository.findByUserIdAndOperationAndIdempotencyKey(1L, PaymentOrder.DEPOSIT_OPERATION, KEY)).thenReturn(Optional.of(order));
        DepositOrderResponse response = service.createOrReplay(1L, KEY, order.getAmount(), "127.0.0.1");
        assertThat(response.status()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(response.canContinuePayment()).isFalse();
        assertThat(response.paymentUrl()).isNull();
        assertThat(order.getPaymentUrl()).isNotNull();
        verifyNoInteractions(gateway);
    }

    @Test
    void shouldHideLinkOnTerminalOrderEvenBeforeExpiry() {
        order.setStatus(PaymentOrderStatus.SUCCESS);
        when(repository.findByOrderIdAndUserId("ORDER_1", 1L)).thenReturn(Optional.of(order));
        assertThat(service.getOrder(1L, "ORDER_1").paymentUrl()).isNull();
    }

    @Test
    void shouldReadLegacyOrderWithoutInventingKeyOrLinkMetadata() {
        order.setIdempotencyKey(null);
        order.setPaymentUrl(null);
        order.setPaymentUrlCreatedAt(null);
        order.setPaymentUrlExpiresAt(null);
        when(repository.findByOrderIdAndUserId("ORDER_1", 1L)).thenReturn(Optional.of(order));
        DepositOrderResponse response = service.getOrder(1L, "ORDER_1");
        assertThat(response.idempotencyKey()).isNull();
        assertThat(response.paymentUrlExpiresAt()).isNull();
        assertThat(response.canContinuePayment()).isFalse();
    }

    @Test
    void shouldReturnNotFoundForAnotherOwnersOrderAndKey() {
        assertThat(assertThrows(BusinessException.class, () -> service.getOrder(2L, "ORDER_1")).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_FOUND);
        assertThat(assertThrows(BusinessException.class, () -> service.getRequest(2L, KEY)).getErrorCode())
                .isEqualTo(ErrorCode.DEPOSIT_REQUEST_NOT_FOUND);
    }

    @Test
    void shouldRejectFractionalLegacyLookupWithoutRounding() {
        order.setAmount(new BigDecimal("10000.50"));
        when(repository.findByOrderIdAndUserId("ORDER_1", 1L)).thenReturn(Optional.of(order));
        assertThat(assertThrows(BusinessException.class, () -> service.getOrder(1L, "ORDER_1")).getErrorCode())
                .isEqualTo(ErrorCode.LEGACY_FRACTIONAL_MONEY);
    }
}
