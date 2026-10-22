package com.minh.paymentsystem.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.payment.dto.VnpayNotification;
import com.minh.paymentsystem.payment.entity.PaymentOrder;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.repository.PaymentOrderRepository;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

class PaymentNotificationServiceTest {

    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 22, 12, 0);
    private final PaymentOrderRepository repository = mock(PaymentOrderRepository.class);
    private final WalletService wallets = mock(WalletService.class);
    private final TransactionService transactions = mock(TransactionService.class);
    private final PaymentNotificationService service = new PaymentNotificationService(repository, wallets, transactions);
    private PaymentOrder order;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).build();
        order = PaymentOrder.builder().user(user).orderId("ORDER_1").amount(new BigDecimal("10000.00"))
                .status(PaymentOrderStatus.PENDING).paymentUrlExpiresAt(PAID_AT.minusHours(1)).build();
        wallet = Wallet.builder().id(1L).user(user).balance(BigDecimal.ZERO).build();
    }

    @Test
    void shouldRejectMissingOrderWithoutAccessingWallet() {
        assertThat(service.process(notification("00", "00")).rspCode()).isEqualTo("01");
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void shouldRejectMismatchedAmountWithoutMutatingOrder() {
        when(repository.findByOrderIdForUpdate("ORDER_1")).thenReturn(Optional.of(order));
        VnpayNotification invalid = new VnpayNotification("ORDER_1", new BigDecimal("1000001"), "00", "00", "123", PAID_AT);
        assertThat(service.process(invalid).rspCode()).isEqualTo("04");
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void shouldNotCreditOrCreateTransactionForTerminalOrder() {
        order.setStatus(PaymentOrderStatus.SUCCESS);
        when(repository.findByOrderIdForUpdate("ORDER_1")).thenReturn(Optional.of(order));
        assertThat(service.process(notification("00", "00")).rspCode()).isEqualTo("02");
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void shouldCommitSuccessMetadataAndCreditEvenAfterLinkExpires() {
        prepareWallet();
        assertThat(service.process(notification("00", "00")).rspCode()).isEqualTo("00");
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.SUCCESS);
        assertThat(order.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(order.getVnpTransactionNo()).isEqualTo("123");
        verify(wallets).credit(wallet.getId(), order.getAmount());
        ArgumentCaptor<CreateTransactionCommand> command = ArgumentCaptor.forClass(CreateTransactionCommand.class);
        verify(transactions).createTransaction(command.capture());
        assertThat(command.getValue().status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(command.getValue().amount()).isEqualByComparingTo("10000");
        verify(repository).saveAndFlush(order);
    }

    @ParameterizedTest
    @CsvSource({
            "24,02",
            "51,02",
            "99,02"
    })
    void shouldRecordOnlyVerifiedFinalFailuresWithoutCredit(String response, String status) {
        prepareWallet();
        assertThat(service.process(notification(response, status)).rspCode()).isEqualTo("00");
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
        assertThat(order.getPaidAt()).isNull();
        verify(wallets, never()).credit(any(), any());
        ArgumentCaptor<CreateTransactionCommand> command = ArgumentCaptor.forClass(CreateTransactionCommand.class);
        verify(transactions).createTransaction(command.capture());
        assertThat(command.getValue().status()).isEqualTo(TransactionStatus.FAILED);
    }

    @ParameterizedTest
    @CsvSource({
            "00,01",
            "00,02",
            "24,00",
            "88,02",
            "99,07"
    })
    void shouldKeepAmbiguousStatusPendingForReconciliation(String response, String status) {
        when(repository.findByOrderIdForUpdate("ORDER_1")).thenReturn(Optional.of(order));
        assertThat(service.process(notification(response, status)).rspCode()).isEqualTo("99");
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        verifyNoInteractions(wallets, transactions);
    }

    @Test
    void shouldPropagateTransactionFailureToRollbackInsteadOfAcknowledging() {
        prepareWallet();
        when(transactions.createTransaction(any())).thenThrow(new DataIntegrityViolationException("failed insert"));
        assertThrows(DataIntegrityViolationException.class, () -> service.process(notification("00", "00")));
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldBlockFractionalWalletBeforeCredit() {
        prepareWallet();
        wallet.setBalance(new BigDecimal("0.50"));
        assertThat(assertThrows(BusinessException.class, () -> service.process(notification("00", "00"))).getErrorCode())
                .isEqualTo(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        verify(wallets, never()).credit(any(), any());
        verifyNoInteractions(transactions);
    }

    @Test
    void shouldVerifyReturnWithoutLockingOrChangingPendingState() {
        when(repository.findByOrderId("ORDER_1")).thenReturn(Optional.of(order));
        assertThat(service.matchesReturn(notification("00", "00"))).isTrue();
        assertThat(order.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        verify(repository, never()).findByOrderIdForUpdate(anyString());
        verifyNoInteractions(wallets, transactions);
    }

    private void prepareWallet() {
        when(repository.findByOrderIdForUpdate("ORDER_1")).thenReturn(Optional.of(order));
        when(wallets.getWalletByUserId(1L)).thenReturn(wallet);
    }

    private VnpayNotification notification(String response, String status) {
        return new VnpayNotification("ORDER_1", new BigDecimal("1000000"), response, status, "123", PAID_AT);
    }
}
