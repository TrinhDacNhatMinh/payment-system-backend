package com.minh.paymentsystem.transfer.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
@Slf4j
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final UserService userService;
    private final WalletService walletService;
    private final TransactionService transactionService;

    @Override
    @Transactional
    public TransferResponse transfer(Long fromUserId, TransferRequest request) {
        User fromUser = getSender(fromUserId);
        validateNotSelfTransfer(fromUser, request.toEmail());
        User toUser = getRecipient(request.toEmail());
        validateTransfer(toUser, request);

        Wallet senderWallet = walletService.getWalletByUserId(fromUserId);
        Wallet recipientWallet = walletService.getWalletByUserId(toUser.getId());

        walletService.debit(senderWallet.getId(), request.amount());
        walletService.credit(recipientWallet.getId(), request.amount());

        String baseReferenceCode = UUID.randomUUID().toString();
        // References share a base but require distinct suffixes for uniqueness.
        Transaction transferOut = createTransferTransaction(senderWallet, request, TransactionType.TRANSFER_OUT, baseReferenceCode + "_OUT");
        Transaction transferIn = createTransferTransaction(recipientWallet, request, TransactionType.TRANSFER_IN, baseReferenceCode + "_IN");
        transferOut.setRelatedTransactionId(transferIn.getId());
        transferIn.setRelatedTransactionId(transferOut.getId());

        log.info("Transfer processed: outgoingTransactionId={}, incomingTransactionId={}", transferOut.getId(), transferIn.getId());
        return new TransferResponse(
                transferOut.getId(),
                senderWallet.getBalance(),
                request.amount(),
                TransactionStatus.SUCCESS,
                LocalDateTime.now());
    }

    private User getSender(Long userId) {
        try {
            return userService.getUserById(userId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, e);
            }
            throw e;
        }
    }

    private void validateNotSelfTransfer(User fromUser, String recipientEmail) {
        if (fromUser.getEmail().equals(recipientEmail)) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }
    }

    private User getRecipient(String email) {
        try {
            return userService.getUserByEmail(email);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND, e);
            }
            throw e;
        }
    }

    private void validateTransfer(User toUser, TransferRequest request) {
        if (toUser.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.RECIPIENT_LOCKED);
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }
    }

    private Transaction createTransferTransaction(Wallet wallet, TransferRequest request, TransactionType type, String referenceCode) {
        return transactionService.createTransaction(new CreateTransactionCommand(
                wallet,
                type,
                TransactionStatus.SUCCESS,
                request.amount(),
                referenceCode,
                request.description()));
    }
}
