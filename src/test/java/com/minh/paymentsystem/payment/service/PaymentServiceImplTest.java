package com.minh.paymentsystem.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.dto.VnpayNotification;

class PaymentServiceImplTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final DepositOrderService orders = mock(DepositOrderService.class);
    private final PaymentNotificationService notifications = mock(PaymentNotificationService.class);
    private final PaymentGatewayService gateway = mock(PaymentGatewayService.class);
    private final PaymentReturnUrl returnUrl = mock(PaymentReturnUrl.class);
    private ValidatorFactory factory;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new PaymentServiceImpl(orders, notifications, gateway, returnUrl, factory.getValidator());
    }

    @AfterEach
    void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9999",
            "50000001",
            "10000.50",
            "0",
            "-10000"
    })
    void shouldValidateAmountBeforeRegisteringKey(String amount) {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.createDepositOrder(
                1L, List.of(KEY.toString()), new DepositRequest(new BigDecimal(amount)), "127.0.0.1"));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(orders);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10000",
            "50000000"
    })
    void shouldAcceptBothAmountBoundaries(String amount) {
        DepositRequest request = new DepositRequest(new BigDecimal(amount));
        service.createDepositOrder(1L, List.of(KEY.toString()), request, "127.0.0.1");
        verify(orders).createOrReplay(1L, KEY, request.amount(), "127.0.0.1");
    }

    @Test
    void shouldRejectMissingOrMultipleKeysBeforeCreatingOrder() {
        DepositRequest request = new DepositRequest(new BigDecimal("10000"));
        assertThrows(BusinessException.class, () -> service.createDepositOrder(1L, null, request, "127.0.0.1"));
        assertThrows(BusinessException.class, () -> service.createDepositOrder(1L, List.of(KEY.toString(), KEY.toString()), request, "127.0.0.1"));
        verifyNoInteractions(orders);
    }

    @Test
    void shouldReadUniqueWinnerAfterInsertTransactionHasRolledBack() {
        BigDecimal amount = new BigDecimal("10000");
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate key");
        when(orders.createOrReplay(1L, KEY, amount, "127.0.0.1"))
                .thenThrow(conflict);
        service.createDepositOrder(1L, List.of(KEY.toString()), new DepositRequest(amount), "127.0.0.1");
        var ordered = inOrder(orders);
        ordered.verify(orders).createOrReplay(1L, KEY, amount, "127.0.0.1");
        ordered.verify(orders).replayAfterConflict(1L, KEY, amount, conflict);
    }

    @Test
    void shouldRejectDuplicateGatewayParametersBeforeVerification() {
        MultiValueMap<String, String> params = successParameters();
        params.add("vnp_Amount", "1");
        assertThat(service.handleCallback(params).rspCode()).isEqualTo("99");
        verifyNoInteractions(gateway, notifications);
    }

    @Test
    void shouldRejectInvalidChecksumBeforeDatabaseWork() {
        assertThat(service.handleCallback(successParameters()).rspCode()).isEqualTo("97");
        verifyNoInteractions(notifications);
    }

    @Test
    void shouldRejectWrongMerchantBeforeDatabaseWork() {
        when(gateway.verifyChecksum(any())).thenReturn(true);
        assertThat(service.handleCallback(successParameters()).rspCode()).isEqualTo("99");
        verifyNoInteractions(notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "20260230120000",
            "invalid",
            ""
    })
    void shouldRejectSuccessWithInvalidPaymentDate(String date) {
        verifyGateway();
        MultiValueMap<String, String> params = successParameters();
        params.set("vnp_PayDate", date);
        assertThat(service.handleCallback(params).rspCode()).isEqualTo("99");
        verifyNoInteractions(notifications);
    }

    @Test
    void shouldReturnGatewayFailureAfterWorkerLockOrCommitError() {
        verifyGateway();
        when(notifications.process(any())).thenThrow(new PessimisticLockingFailureException("busy"))
                .thenThrow(new DataIntegrityViolationException("commit failed"));
        assertThat(service.handleCallback(successParameters()).rspCode()).isEqualTo("99");
        assertThat(service.handleCallback(successParameters()).rspCode()).isEqualTo("99");
    }

    @Test
    void shouldReturnAcknowledgementOnlyAfterWorkerReturns() {
        verifyGateway();
        when(notifications.process(any())).thenReturn(new VnpayCallbackResponse("00", "Confirm Success"));
        assertThat(service.handleCallback(successParameters()).rspCode()).isEqualTo("00");
        verify(notifications).process(any(VnpayNotification.class));
    }

    @Test
    void shouldRedirectVerifiedReturnWithoutProcessingNotification() {
        verifyGateway();
        when(notifications.matchesReturn(any())).thenReturn(true);
        URI target = URI.create("http://localhost:5173/payment/result?orderId=ORDER_1");
        when(returnUrl.forVerifiedReturn(eq("ORDER_1"), anyMap())).thenReturn(target);
        assertThat(service.processReturn(successParameters())).isEqualTo(target);
        verify(notifications, never()).process(any());
        verifyNoInteractions(orders);
    }

    @Test
    void shouldRedirectUnverifiedReturnWithoutExposingOrderId() {
        URI invalid = URI.create("http://localhost:5173/payment/result?verification=invalid");
        when(returnUrl.invalid()).thenReturn(invalid);
        assertThat(service.processReturn(successParameters())).isEqualTo(invalid);
        verifyNoInteractions(notifications);
    }

    @Test
    void shouldReportReturnVerificationUnavailableOnDatabaseFailure() {
        verifyGateway();
        when(notifications.matchesReturn(any())).thenThrow(new IllegalStateException("database unavailable"));
        URI unavailable = URI.create("http://localhost:5173/payment/result?verification=unavailable");
        when(returnUrl.unavailable()).thenReturn(unavailable);
        assertThat(service.processReturn(successParameters())).isEqualTo(unavailable);
    }

    private void verifyGateway() {
        when(gateway.verifyChecksum(any())).thenReturn(true);
        when(gateway.matchesMerchant(any())).thenReturn(true);
    }

    static MultiValueMap<String, String> successParameters() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.set("vnp_TxnRef", "ORDER_1");
        params.set("vnp_Amount", "1000000");
        params.set("vnp_TmnCode", "TESTCODE");
        params.set("vnp_ResponseCode", "00");
        params.set("vnp_TransactionStatus", "00");
        params.set("vnp_TransactionNo", "123456");
        params.set("vnp_PayDate", "20261022120000");
        params.set("vnp_SecureHash", "test-signature");
        return params;
    }
}
