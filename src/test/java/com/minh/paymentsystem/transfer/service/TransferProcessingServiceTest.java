package com.minh.paymentsystem.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.entity.TransferInstruction;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.transfer.repository.TransferInstructionRepository;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
class TransferProcessingServiceTest {

    private static final UUID KEY = UUID.randomUUID();

    @Mock
    private TransferInstructionRepository repository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private UserService userService;
    @Mock
    private WalletService walletService;
    @Mock
    private TransactionService transactionService;
    @InjectMocks
    private TransferProcessingService service;

    private TransferRequest request;
    private TransferInstruction instruction;
    private User sender;
    private User recipient;
    private Wallet senderWallet;
    private Wallet recipientWallet;

    @BeforeEach
    void setUp() {
        request = new TransferRequest("recipient@example.com", new BigDecimal("1000.50"), null);
        sender = User.builder().id(1L).email("sender@example.com").status(UserStatus.ACTIVE).build();
        recipient = User.builder().id(2L).email(request.toEmail()).status(UserStatus.ACTIVE).build();
        senderWallet = Wallet.builder().id(10L).user(sender).balance(new BigDecimal("5000")).build();
        recipientWallet = Wallet.builder().id(20L).user(recipient).balance(BigDecimal.ZERO).build();
        instruction = TransferInstruction.builder()
                .id(30L).user(sender).operation(TransferInstruction.OPERATION)
                .payloadSchema(TransferInstruction.PAYLOAD_SCHEMA).idempotencyKey(KEY)
                .toEmail(request.toEmail()).amount(request.amount()).state(TransferRequestState.PENDING).build();
        when(repository.findByUserIdAndOperationAndIdempotencyKey(1L, TransferInstruction.OPERATION, KEY))
                .thenReturn(Optional.of(instruction));
    }

    @Test
    void shouldRejectPayloadMismatchWithoutLockOrMoneyChanges() {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.process(1L, KEY,
                new TransferRequest(request.toEmail(), request.amount(), "")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        verify(repository, never()).findByIdForUpdate(any());
        verifyNoInteractions(walletService, transactionService);
    }

    @Test
    void shouldReturnInProgressWithoutCallingWallets() {
        when(repository.findByIdForUpdate(30L)).thenThrow(new CannotAcquireLockException("busy"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.process(1L, KEY, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_IN_PROGRESS);
        assertThat(instruction.getState()).isEqualTo(TransferRequestState.PENDING);
        verifyNoInteractions(walletService, transactionService);
    }

    @Test
    void shouldReplayReceiptWithoutReadingCurrentWallets() {
        instruction.markSucceeded(Transaction.builder().id(100L).build(), new BigDecimal("3999.50"), LocalDateTime.now());

        var response = service.process(1L, KEY,
                new TransferRequest(request.toEmail(), new BigDecimal("1000.5"), null));

        assertThat(response.result().transactionId()).isEqualTo(100L);
        assertThat(response.result().fromWalletBalance()).isEqualByComparingTo("3999.50");
        verify(repository, never()).findByIdForUpdate(any());
        verifyNoInteractions(userService, walletService, transactionService);
    }

    @Test
    void shouldRecheckStateAfterAcquiringLock() {
        when(repository.findByIdForUpdate(30L)).thenReturn(Optional.of(instruction));
        doAnswer(invocation -> {
            instruction.markRejected(ErrorCode.RECIPIENT_LOCKED);
            return null;
        }).when(entityManager).refresh(instruction);

        assertThat(service.process(1L, KEY, request).error().code()).isEqualTo("RECIPIENT_LOCKED");
        verifyNoInteractions(userService, walletService, transactionService);
    }

    @Test
    void shouldPersistBusinessRejectionWithoutCallingDebit() {
        prepareWallets();
        senderWallet.setBalance(BigDecimal.ZERO);

        var response = service.process(1L, KEY, request);

        assertThat(response.state()).isEqualTo(TransferRequestState.REJECTED);
        assertThat(response.error().code()).isEqualTo("WALLET_INSUFFICIENT_BALANCE");
        verify(walletService, never()).debit(any(), any());
        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldRejectMissingRecipientUsingOptionalRead() {
        when(repository.findByIdForUpdate(30L)).thenReturn(Optional.of(instruction));
        when(userService.getUserById(1L)).thenReturn(sender);
        when(userService.findUserByEmail(request.toEmail())).thenReturn(Optional.empty());

        assertThat(service.process(1L, KEY, request).error().code()).isEqualTo("RECIPIENT_NOT_FOUND");
        verifyNoInteractions(walletService, transactionService);
    }

    @Test
    void shouldRejectSelfTransferBeforeRecipientLookup() {
        when(repository.findByIdForUpdate(30L)).thenReturn(Optional.of(instruction));
        sender.setEmail(request.toEmail());
        when(userService.getUserById(1L)).thenReturn(sender);

        assertThat(service.process(1L, KEY, request).error().code()).isEqualTo("SELF_TRANSFER_NOT_ALLOWED");
        verify(userService, never()).findUserByEmail(any());
        verifyNoInteractions(walletService, transactionService);
    }

    @Test
    void shouldNotTurnInfrastructureFailureIntoRejection() {
        when(repository.findByIdForUpdate(30L)).thenReturn(Optional.of(instruction));
        when(userService.getUserById(1L)).thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.process(1L, KEY, request));

        assertThat(instruction.getState()).isEqualTo(TransferRequestState.PENDING);
    }

    @Test
    void shouldPropagateOptimisticFailureWithoutMarkingTerminal() {
        prepareWallets();
        doThrow(new OptimisticLockingFailureException("version conflict"))
                .when(walletService).credit(20L, request.amount());

        assertThrows(OptimisticLockingFailureException.class, () -> service.process(1L, KEY, request));

        assertThat(instruction.getState()).isEqualTo(TransferRequestState.PENDING);
        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldCreateLinkedTransactionsAndSnapshotActualBalance() {
        prepareWallets();
        Transaction outgoing = Transaction.builder().id(100L).build();
        Transaction incoming = Transaction.builder().id(200L).build();
        when(transactionService.createTransaction(argThat(command -> command != null && command.type() == TransactionType.TRANSFER_OUT)))
                .thenReturn(outgoing);
        when(transactionService.createTransaction(argThat(command -> command != null && command.type() == TransactionType.TRANSFER_IN)))
                .thenReturn(incoming);
        doAnswer(invocation -> {
            senderWallet.setBalance(senderWallet.getBalance().subtract(request.amount()));
            return null;
        }).when(walletService).debit(10L, request.amount());

        var response = service.process(1L, KEY, request);

        assertThat(response.state()).isEqualTo(TransferRequestState.SUCCEEDED);
        assertThat(response.result().transactionId()).isEqualTo(100L);
        assertThat(response.result().fromWalletBalance()).isEqualByComparingTo("3999.50");
        assertThat(outgoing.getRelatedTransactionId()).isEqualTo(200L);
        assertThat(incoming.getRelatedTransactionId()).isEqualTo(100L);
        verify(walletService).credit(20L, request.amount());
    }

    private void prepareWallets() {
        when(repository.findByIdForUpdate(30L)).thenReturn(Optional.of(instruction));
        when(userService.getUserById(1L)).thenReturn(sender);
        when(userService.findUserByEmail(request.toEmail())).thenReturn(Optional.of(recipient));
        when(walletService.findWalletByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletService.findWalletByUserId(2L)).thenReturn(Optional.of(recipientWallet));
    }
}
