package com.minh.paymentsystem.transfer.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferRequestError;
import com.minh.paymentsystem.transfer.dto.TransferRequestResponse;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.entity.TransferInstruction;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.transfer.repository.TransferInstructionRepository;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferProcessingService {

    private static final ZoneId TRANSFER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final TransferInstructionRepository repository;
    private final EntityManager entityManager;
    private final UserService userService;
    private final WalletService walletService;
    private final TransactionService transactionService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public TransferRequestResponse process(Long userId, UUID key, TransferRequest request) {
        TransferInstruction instruction = getInstruction(userId, key);
        validatePayload(instruction, request);
        if (instruction.getState() != TransferRequestState.PENDING) {
            return toResponse(instruction);
        }
        instruction = lockInstruction(instruction.getId());
        validatePayload(instruction, request);
        if (instruction.getState() != TransferRequestState.PENDING) {
            return toResponse(instruction);
        }

        TransferWallets wallets;
        try {
            wallets = prepareTransfer(userId, request);
        } catch (BusinessException exception) {
            if (!isRejection(exception.getErrorCode())) {
                throw exception;
            }
            // Preparation only reads data; no nested service exception has marked this transaction rollback-only.
            instruction.markRejected(exception.getErrorCode());
            return toResponse(instruction);
        }
        executeTransfer(instruction, wallets, request);
        return toResponse(instruction);
    }

    @Transactional(readOnly = true)
    public TransferRequestResponse getRequest(Long userId, UUID key) {
        return toResponse(getInstruction(userId, key));
    }

    private TransferInstruction getInstruction(Long userId, UUID key) {
        return repository.findByUserIdAndOperationAndIdempotencyKey(userId, TransferInstruction.OPERATION, key)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_REQUEST_NOT_FOUND));
    }

    private void validatePayload(TransferInstruction instruction, TransferRequest request) {
        if (!TransferInstruction.PAYLOAD_SCHEMA.equals(instruction.getPayloadSchema())
                || !instruction.getToEmail().equals(request.toEmail())
                || instruction.getAmount().compareTo(request.amount()) != 0
                || !Objects.equals(instruction.getDescription(), request.description())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private TransferInstruction lockInstruction(Long id) {
        try {
            TransferInstruction instruction = repository.findByIdForUpdate(id)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_REQUEST_NOT_FOUND));
            // The initial unlocked query may have placed an older state in the persistence context.
            entityManager.refresh(instruction);
            return instruction;
        } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
            throw new BusinessException(ErrorCode.TRANSFER_IN_PROGRESS, exception);
        }
    }

    private TransferWallets prepareTransfer(Long userId, TransferRequest request) {
        User sender = userService.getUserById(userId);
        if (sender.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (sender.getEmail().equals(request.toEmail())) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }
        User recipient = userService.findUserByEmail(request.toEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (recipient.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.RECIPIENT_LOCKED);
        }
        Wallet senderWallet = walletService.findWalletByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        Wallet recipientWallet = walletService.findWalletByUserId(recipient.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        if (senderWallet.getBalance().compareTo(request.amount()) < 0) {
            throw new BusinessException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }
        return new TransferWallets(senderWallet, recipientWallet);
    }

    private boolean isRejection(ErrorCode errorCode) {
        return switch (errorCode) {
            case SELF_TRANSFER_NOT_ALLOWED, RECIPIENT_NOT_FOUND, RECIPIENT_LOCKED,
                    WALLET_NOT_FOUND, WALLET_INSUFFICIENT_BALANCE ->
                true;
            default -> false;
        };
    }

    private void executeTransfer(TransferInstruction instruction, TransferWallets wallets, TransferRequest request) {
        walletService.debit(wallets.sender().getId(), request.amount());
        walletService.credit(wallets.recipient().getId(), request.amount());

        String reference = UUID.randomUUID().toString();
        Transaction outgoing = createTransaction(wallets.sender(), request, TransactionType.TRANSFER_OUT, reference + "_OUT");
        Transaction incoming = createTransaction(wallets.recipient(), request, TransactionType.TRANSFER_IN, reference + "_IN");
        outgoing.setRelatedTransactionId(incoming.getId());
        incoming.setRelatedTransactionId(outgoing.getId());
        instruction.markSucceeded(outgoing, wallets.sender().getBalance(), LocalDateTime.now(TRANSFER_ZONE).truncatedTo(ChronoUnit.MICROS));
        log.info("Transfer processed: outgoingTransactionId={}, incomingTransactionId={}", outgoing.getId(), incoming.getId());
    }

    private Transaction createTransaction(Wallet wallet, TransferRequest request, TransactionType type, String reference) {
        return transactionService.createTransaction(new CreateTransactionCommand(
                wallet, type, TransactionStatus.SUCCESS, request.amount(), reference, request.description()));
    }

    private TransferRequestResponse toResponse(TransferInstruction instruction) {
        TransferResponse result = null;
        TransferRequestError error = null;
        if (instruction.getState() == TransferRequestState.SUCCEEDED) {
            result = new TransferResponse(
                    instruction.getTransaction().getId(), instruction.getReceiptBalance(),
                    instruction.getReceiptAmount(), instruction.getReceiptStatus(), instruction.getReceiptCreatedAt());
        } else if (instruction.getState() == TransferRequestState.REJECTED) {
            error = new TransferRequestError(instruction.getErrorHttpStatus(), instruction.getErrorCode(), instruction.getErrorMessage());
        }
        return new TransferRequestResponse(
                instruction.getIdempotencyKey(), instruction.getState(),
                new TransferRequest(instruction.getToEmail(), instruction.getAmount(), instruction.getDescription()),
                result, error);
    }

    private record TransferWallets(
            Wallet sender,
            Wallet recipient) {
    }
}
