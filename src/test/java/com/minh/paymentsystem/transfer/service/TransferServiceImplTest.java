package com.minh.paymentsystem.transfer.service;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.service.impl.TransferServiceImpl;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import com.minh.paymentsystem.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private TransferServiceImpl transferService;

    private User senderUser;
    private User recipientUser;
    private Wallet senderWallet;
    private Wallet recipientWallet;
    private TransferRequest request;

    @BeforeEach
    void setUp() {
        senderUser = User.builder()
                .id(1L)
                .email("sender@example.com")
                .status(UserStatus.ACTIVE)
                .build();

        recipientUser = User.builder()
                .id(2L)
                .email("recipient@example.com")
                .status(UserStatus.ACTIVE)
                .build();

        senderWallet = Wallet.builder()
                .id(10L)
                .user(senderUser)
                .balance(new BigDecimal("500000"))
                .build();

        recipientWallet = Wallet.builder()
                .id(20L)
                .user(recipientUser)
                .balance(new BigDecimal("0"))
                .build();

        request = new TransferRequest("recipient@example.com", new BigDecimal("100000"), "Transfer money");
    }

    @Nested
    @DisplayName("transfer()")
    class TransferTests {

        @Test
        void transfer_selfTransfer_throwsBusinessException() {
            // given
            TransferRequest selfRequest = new TransferRequest("sender@example.com", new BigDecimal("100000"), "Self transfer");
            when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, selfRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }

        @Test
        void transfer_recipientNotFound_throwsBusinessException() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));
            when(userRepository.findByEmail(request.toEmail())).thenReturn(Optional.empty());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, request));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPIENT_NOT_FOUND);
        }

        @Test
        void transfer_recipientLocked_throwsBusinessException() {
            // given
            User lockedUser = User.builder().email("recipient@example.com").status(UserStatus.LOCKED).build();
            when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));
            when(userRepository.findByEmail(request.toEmail())).thenReturn(Optional.of(lockedUser));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, request));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPIENT_LOCKED);
        }

        @Test
        void transfer_amountOutOfRange_throwsBusinessException() {
            // given
            TransferRequest negativeAmountRequest = new TransferRequest("recipient@example.com", new BigDecimal("-50000"), "Negative");
            when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));
            when(userRepository.findByEmail(request.toEmail())).thenReturn(Optional.of(recipientUser));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, negativeAmountRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }

        @Test
        void transfer_insufficientBalance_throwsBusinessException() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));
            when(userRepository.findByEmail(request.toEmail())).thenReturn(Optional.of(recipientUser));
            when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
            when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(recipientWallet));
            
            // Simulating WalletService throwing the exception during debit
            doThrow(new BusinessException(ErrorCode.WALLET_INSUFFICIENT_BALANCE))
                    .when(walletService).debit(10L, request.amount());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, request));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WALLET_INSUFFICIENT_BALANCE);

            verify(walletService, never()).credit(any(), any());
            verify(transactionService, never()).createTransaction(any(), any(), any(), any(), anyString(), anyString());
        }

        @Test
        void transfer_concurrentModification_throwsOptimisticLockingFailureException() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));
            when(userRepository.findByEmail(request.toEmail())).thenReturn(Optional.of(recipientUser));
            when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
            when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(recipientWallet));

            // Simulating optimistic locking exception on credit
            doThrow(OptimisticLockingFailureException.class).when(walletService).credit(20L, request.amount());

            // when & then
            assertThrows(OptimisticLockingFailureException.class, () -> transferService.transfer(1L, request));

            verify(transactionService, never()).createTransaction(any(), any(), any(), any(), anyString(), anyString());
        }

        @Test
        void transfer_validRequest_createsTransactionsAndUpdatesWallets() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(senderUser));
            when(userRepository.findByEmail(request.toEmail())).thenReturn(Optional.of(recipientUser));
            when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
            when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(recipientWallet));

            Transaction transferOut = Transaction.builder().id(100L).build();
            Transaction transferIn = Transaction.builder().id(200L).build();

            when(transactionService.createTransaction(eq(senderWallet), eq(TransactionType.TRANSFER_OUT), eq(TransactionStatus.SUCCESS), eq(request.amount()), anyString(), eq(request.description())))
                    .thenReturn(transferOut);
            when(transactionService.createTransaction(eq(recipientWallet), eq(TransactionType.TRANSFER_IN), eq(TransactionStatus.SUCCESS), eq(request.amount()), anyString(), eq(request.description())))
                    .thenReturn(transferIn);

            // when
            TransferResponse response = transferService.transfer(1L, request);

            // then
            verify(walletService, times(1)).debit(10L, request.amount());
            verify(walletService, times(1)).credit(20L, request.amount());
            verify(transactionService, times(2)).createTransaction(any(), any(), any(), any(), anyString(), anyString());

            // verify bidirectional links
            assertThat(transferOut.getRelatedTransactionId()).isEqualTo(200L);
            assertThat(transferIn.getRelatedTransactionId()).isEqualTo(100L);

            // verify response
            assertThat(response.transactionId()).isEqualTo(100L);
            assertThat(response.amount()).isEqualByComparingTo(request.amount());
            assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        }
    }
}
