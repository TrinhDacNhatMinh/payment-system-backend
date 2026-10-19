package com.minh.paymentsystem.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private UserService userService;

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
        void transfer_senderNotFound_returnsAccessDenied() {
            BusinessException original = new BusinessException(ErrorCode.USER_NOT_FOUND);
            when(userService.getUserById(1L)).thenThrow(original);

            BusinessException exception = assertThrows(BusinessException.class, () -> transferService.transfer(1L, request));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
            assertThat(exception.getCause()).isSameAs(original);
            verifyNoInteractions(walletService, transactionService);
        }

        @Test
        void transfer_unrelatedSenderError_propagatesOriginalException() {
            BusinessException failure = new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            when(userService.getUserById(1L)).thenThrow(failure);

            assertThat(assertThrows(BusinessException.class, () -> transferService.transfer(1L, request))).isSameAs(failure);
            verifyNoInteractions(walletService, transactionService);
        }

        @Test
        void transfer_unrelatedRecipientError_propagatesOriginalException() {
            BusinessException failure = new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(request.toEmail())).thenThrow(failure);

            assertThat(assertThrows(BusinessException.class, () -> transferService.transfer(1L, request))).isSameAs(failure);
            verifyNoInteractions(walletService, transactionService);
        }

        @Test
        void transfer_selfTransfer_throwsBusinessException() {
            // given
            TransferRequest selfRequest = new TransferRequest("sender@example.com", new BigDecimal("100000"), "Self transfer");
            when(userService.getUserById(1L)).thenReturn(senderUser);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, selfRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }

        @Test
        void transfer_recipientNotFound_throwsBusinessException() {
            // given
            BusinessException original = new BusinessException(ErrorCode.USER_NOT_FOUND);
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(request.toEmail())).thenThrow(original);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, request));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPIENT_NOT_FOUND);
            assertThat(ex.getCause()).isSameAs(original);
        }

        @Test
        void transfer_selfTransferWithInvalidAmount_preservesSelfTransferError() {
            TransferRequest invalidRequest = new TransferRequest(senderUser.getEmail(), BigDecimal.ZERO, "Self transfer");
            when(userService.getUserById(1L)).thenReturn(senderUser);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> transferService.transfer(1L, invalidRequest));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
            verify(userService, never()).getUserByEmail(anyString());
            verifyNoInteractions(walletService, transactionService);
        }

        @Test
        void transfer_lockedRecipientWithInvalidAmount_preservesRecipientLockedError() {
            recipientUser.setStatus(UserStatus.LOCKED);
            TransferRequest invalidRequest = new TransferRequest(recipientUser.getEmail(), BigDecimal.ZERO, "Locked recipient");
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(invalidRequest.toEmail())).thenReturn(recipientUser);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> transferService.transfer(1L, invalidRequest));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RECIPIENT_LOCKED);
            verifyNoInteractions(walletService, transactionService);
        }

        @Test
        void transfer_recipientLocked_throwsBusinessException() {
            // given
            User lockedUser = User.builder()
                    .email("recipient@example.com")
                    .status(UserStatus.LOCKED)
                    .build();
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(request.toEmail())).thenReturn(lockedUser);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, request));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECIPIENT_LOCKED);
        }

        @Test
        void transfer_amountOutOfRange_throwsBusinessException() {
            // given
            TransferRequest negativeAmountRequest = new TransferRequest("recipient@example.com", new BigDecimal("-50000"), "Negative");
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(request.toEmail())).thenReturn(recipientUser);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, negativeAmountRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }

        @Test
        void transfer_insufficientBalance_throwsBusinessException() {
            // given
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(request.toEmail())).thenReturn(recipientUser);
            when(walletService.getWalletByUserId(1L)).thenReturn(senderWallet);
            when(walletService.getWalletByUserId(2L)).thenReturn(recipientWallet);

            // Simulating WalletService throwing the exception during debit
            doThrow(new BusinessException(ErrorCode.WALLET_INSUFFICIENT_BALANCE))
                    .when(walletService).debit(10L, request.amount());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> transferService.transfer(1L, request));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.WALLET_INSUFFICIENT_BALANCE);

            verify(walletService, never()).credit(any(), any());
            verify(transactionService, never()).createTransaction(any(CreateTransactionCommand.class));
        }

        @Test
        void transfer_concurrentModification_throwsOptimisticLockingFailureException() {
            // given
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(request.toEmail())).thenReturn(recipientUser);
            when(walletService.getWalletByUserId(1L)).thenReturn(senderWallet);
            when(walletService.getWalletByUserId(2L)).thenReturn(recipientWallet);

            // Simulating optimistic locking exception on credit
            doThrow(OptimisticLockingFailureException.class).when(walletService).credit(20L, request.amount());

            // when & then
            assertThrows(OptimisticLockingFailureException.class, () -> transferService.transfer(1L, request));

            verify(transactionService, never()).createTransaction(any(CreateTransactionCommand.class));
        }

        @Test
        void transfer_validRequest_createsTransactionsAndUpdatesWallets() {
            // given
            when(userService.getUserById(1L)).thenReturn(senderUser);
            when(userService.getUserByEmail(request.toEmail())).thenReturn(recipientUser);
            when(walletService.getWalletByUserId(1L)).thenReturn(senderWallet);
            when(walletService.getWalletByUserId(2L)).thenReturn(recipientWallet);

            Transaction transferOut = Transaction.builder()
                    .id(100L)
                    .build();
            Transaction transferIn = Transaction.builder()
                    .id(200L)
                    .build();

            when(transactionService.createTransaction(argThat(command -> command != null && command.type() == TransactionType.TRANSFER_OUT)))
                    .thenReturn(transferOut);
            when(transactionService.createTransaction(argThat(command -> command != null && command.type() == TransactionType.TRANSFER_IN)))
                    .thenReturn(transferIn);
            doAnswer(invocation -> {
                senderWallet.setBalance(senderWallet.getBalance().subtract(request.amount()));
                return null;
            }).when(walletService).debit(senderWallet.getId(), request.amount());

            // when
            TransferResponse response = transferService.transfer(1L, request);

            // then
            verify(userService, times(1)).getUserByEmail(request.toEmail());
            verify(walletService, times(1)).debit(10L, request.amount());
            verify(walletService, times(1)).credit(20L, request.amount());
            ArgumentCaptor<CreateTransactionCommand> captor = ArgumentCaptor.forClass(CreateTransactionCommand.class);
            verify(transactionService, times(2)).createTransaction(captor.capture());
            CreateTransactionCommand outgoing = captor.getAllValues().get(0);
            CreateTransactionCommand incoming = captor.getAllValues().get(1);
            assertThat(outgoing.wallet()).isSameAs(senderWallet);
            assertThat(incoming.wallet()).isSameAs(recipientWallet);
            assertThat(outgoing.type()).isEqualTo(TransactionType.TRANSFER_OUT);
            assertThat(incoming.type()).isEqualTo(TransactionType.TRANSFER_IN);
            assertThat(outgoing.status()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(incoming.status()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(outgoing.amount()).isEqualByComparingTo(request.amount());
            assertThat(incoming.amount()).isEqualByComparingTo(request.amount());
            assertThat(outgoing.description()).isEqualTo(request.description());
            assertThat(incoming.description()).isEqualTo(request.description());
            assertThat(outgoing.referenceCode()).endsWith("_OUT");
            assertThat(incoming.referenceCode()).isEqualTo(outgoing.referenceCode().replace("_OUT", "_IN"));

            // verify bidirectional links
            assertThat(transferOut.getRelatedTransactionId()).isEqualTo(200L);
            assertThat(transferIn.getRelatedTransactionId()).isEqualTo(100L);

            // verify response
            assertThat(response.transactionId()).isEqualTo(100L);
            assertThat(response.amount()).isEqualByComparingTo(request.amount());
            assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(response.fromWalletBalance()).isEqualByComparingTo("400000");
        }
    }
}
