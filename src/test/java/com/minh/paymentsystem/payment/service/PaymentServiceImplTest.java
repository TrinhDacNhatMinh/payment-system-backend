package com.minh.paymentsystem.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
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

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private WalletService walletService;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User testUser;
    private Wallet testWallet;
    private PaymentOrder pendingOrder;
    private Map<String, String> callbackParams;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();
        testWallet = Wallet.builder()
                .id(10L)
                .user(testUser)
                .balance(new BigDecimal("100000"))
                .build();

        pendingOrder = PaymentOrder.builder()
                .id(100L)
                .orderId("VNPAY_123")
                .user(testUser)
                .amount(new BigDecimal("50000"))
                .status(PaymentOrderStatus.PENDING)
                .build();

        callbackParams = new HashMap<>();
        callbackParams.put("vnp_TxnRef", "VNPAY_123");
    }

    @Test
    void shouldRejectUnverifiedReturnWithoutLookingUpOrder() {
        when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(false);

        assertThat(paymentService.processReturn(callbackParams)).isEqualTo("Invalid checksum. Payment verification failed.");

        verifyNoInteractions(paymentOrderRepository, walletService, transactionService);
    }

    @Test
    void shouldKeepMissingOrderErrorForVerifiedReturn() {
        when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(true);
        when(paymentOrderRepository.findByOrderId("VNPAY_123")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> paymentService.processReturn(callbackParams));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_FOUND);
        verifyNoInteractions(walletService, transactionService);
    }

    @Test
    void shouldReturnSuccessfulPaymentMessageWithoutUpdatingWallet() {
        callbackParams.put("vnp_ResponseCode", "00");
        when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(true);
        when(paymentOrderRepository.findByOrderId("VNPAY_123")).thenReturn(Optional.of(pendingOrder));

        assertThat(paymentService.processReturn(callbackParams)).isEqualTo("Payment successful for order: VNPAY_123");

        verifyNoInteractions(walletService, transactionService);
        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
    }

    @Test
    void shouldReturnFailedPaymentMessageWithoutUpdatingWallet() {
        callbackParams.put("vnp_ResponseCode", "24");
        when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(true);
        when(paymentOrderRepository.findByOrderId("VNPAY_123")).thenReturn(Optional.of(pendingOrder));

        assertThat(paymentService.processReturn(callbackParams)).isEqualTo("Payment failed for order: VNPAY_123");

        verifyNoInteractions(walletService, transactionService);
        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
    }

    @Test
    void shouldRejectFractionalDepositBeforeCreatingAnOrder() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> paymentService.createDepositOrder(1L, new DepositRequest(new BigDecimal("10000.001")), "127.0.0.1"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        verifyNoInteractions(paymentOrderRepository, paymentGatewayService, walletService, transactionService);
    }

    @Test
    void shouldLeaveLegacyFractionalPendingOrderUnchangedOnCallback() {
        pendingOrder.setAmount(new BigDecimal("50000.50"));
        when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(true);
        when(paymentOrderRepository.findByOrderId("VNPAY_123")).thenReturn(Optional.of(pendingOrder));

        BusinessException exception = assertThrows(BusinessException.class, () -> paymentService.handleCallback(callbackParams));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(pendingOrder.getAmount()).isEqualByComparingTo("50000.50");
        verifyNoInteractions(walletService, transactionService);
    }

    @Nested
    @DisplayName("createDepositOrder()")
    class CreateDepositOrderTests {

        @Test
        void createDepositOrder_amountTooLow_throwsBusinessException() {
            // given
            DepositRequest request = new DepositRequest(new BigDecimal("5000"));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> paymentService.createDepositOrder(1L, request, "127.0.0.1"));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }

        @Test
        void createDepositOrder_amountTooHigh_throwsBusinessException() {
            // given
            DepositRequest request = new DepositRequest(new BigDecimal("100000000"));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> paymentService.createDepositOrder(1L, request, "127.0.0.1"));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }

        @Test
        void createDepositOrder_validAmount_createsPendingOrderAndReturnsUrl() {
            // given
            DepositRequest request = new DepositRequest(new BigDecimal("50000"));
            when(userService.getUserById(1L)).thenReturn(testUser);
            when(paymentGatewayService.buildPaymentUrl(anyString(), eq(request.amount()), eq("127.0.0.1")))
                    .thenReturn("http://vnpay.vn/pay?id=123");

            ArgumentCaptor<PaymentOrder> orderCaptor = ArgumentCaptor.forClass(PaymentOrder.class);

            // when
            DepositResponse response = paymentService.createDepositOrder(1L, request, "127.0.0.1");

            // then
            verify(paymentOrderRepository, times(1)).save(orderCaptor.capture());
            assertThat(orderCaptor.getValue().getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
            assertThat(orderCaptor.getValue().getAmount()).isEqualByComparingTo(request.amount());

            assertThat(response.paymentUrl()).isEqualTo("http://vnpay.vn/pay?id=123");
            assertThat(response.orderId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("handleCallback()")
    class HandleCallbackTests {

        @Test
        void handleCallback_invalidChecksum_returnsErrorResponse() {
            // given
            when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(false);

            // when
            VnpayCallbackResponse response = paymentService.handleCallback(callbackParams);

            // then
            assertThat(response.rspCode()).isEqualTo("97");
            assertThat(response.message()).isEqualTo("Invalid Checksum");
            verify(paymentOrderRepository, never()).findByOrderId(anyString());
        }

        @Test
        void handleCallback_orderAlreadyProcessed_doesNotCreditWalletAgain() {
            // given
            when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(true);
            pendingOrder.setStatus(PaymentOrderStatus.SUCCESS);
            when(paymentOrderRepository.findByOrderId("VNPAY_123")).thenReturn(Optional.of(pendingOrder));

            // when
            VnpayCallbackResponse response = paymentService.handleCallback(callbackParams);

            // then
            assertThat(response.rspCode()).isEqualTo("02");
            assertThat(response.message()).isEqualTo("Order already confirmed");

            verify(walletService, never()).credit(any(), any());
            verify(transactionService, never()).createTransaction(any(CreateTransactionCommand.class));
        }

        @Test
        void handleCallback_vnpaySuccess_updatesOrderToSuccessAndCreditsWallet() {
            // given
            callbackParams.put("vnp_ResponseCode", "00");
            when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(true);
            when(paymentOrderRepository.findByOrderId("VNPAY_123")).thenReturn(Optional.of(pendingOrder));
            when(walletService.getWalletByUserId(1L)).thenReturn(testWallet);

            // when
            VnpayCallbackResponse response = paymentService.handleCallback(callbackParams);

            // then
            assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.SUCCESS);
            verify(walletService, times(1)).credit(10L, new BigDecimal("50000"));
            verify(transactionService, times(1)).createTransaction(new CreateTransactionCommand(
                    testWallet, TransactionType.DEPOSIT, TransactionStatus.SUCCESS,
                    new BigDecimal("50000"), "VNPAY_123", "VNPay deposit successful"));

            assertThat(response.rspCode()).isEqualTo("00");
        }

        @Test
        void handleCallback_vnpayFailed_updatesOrderToFailedAndDoesNotCreditWallet() {
            // given
            callbackParams.put("vnp_ResponseCode", "24"); // Some error code
            when(paymentGatewayService.verifyChecksum(callbackParams)).thenReturn(true);
            when(paymentOrderRepository.findByOrderId("VNPAY_123")).thenReturn(Optional.of(pendingOrder));
            when(walletService.getWalletByUserId(1L)).thenReturn(testWallet);

            // when
            VnpayCallbackResponse response = paymentService.handleCallback(callbackParams);

            // then
            assertThat(pendingOrder.getStatus()).isEqualTo(PaymentOrderStatus.FAILED);
            verify(walletService, never()).credit(any(), any());
            verify(transactionService, times(1)).createTransaction(new CreateTransactionCommand(
                    testWallet, TransactionType.DEPOSIT, TransactionStatus.FAILED,
                    new BigDecimal("50000"), "VNPAY_123", "VNPay deposit failed"));

            assertThat(response.rspCode()).isEqualTo("00");
        }
    }
}
