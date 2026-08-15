package com.minh.paymentsystem.transfer.service.impl;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.service.TransferService;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import com.minh.paymentsystem.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final TransactionService transactionService;

    @Override
    @Transactional
    public TransferResponse transfer(Long fromUserId, TransferRequest request) {
        // 1. Validate and get recipient info
        validateTransfer(fromUserId, request);

        // 2. Fetch sender wallet and recipient wallet
        User toUser = userRepository.findByEmail(request.toEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND));

        Wallet senderWallet = walletRepository.findByUserId(fromUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        Wallet recipientWallet = walletRepository.findByUserId(toUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        // 3. Debit sender
        walletService.debit(senderWallet.getId(), request.amount());

        // 4. Credit recipient
        walletService.credit(recipientWallet.getId(), request.amount());

        // 5. Generate base reference code (using UUID)
        String baseReferenceCode = UUID.randomUUID().toString();

        // 6. Create TRANSFER_OUT transaction for sender
        // Note: referenceCode must be unique, so we append _OUT and _IN suffixes
        Transaction transferOut = transactionService.createTransaction(
                senderWallet,
                TransactionType.TRANSFER_OUT,
                TransactionStatus.SUCCESS,
                request.amount(),
                baseReferenceCode + "_OUT",
                request.description()
        );

        // 7. Create TRANSFER_IN transaction for recipient
        Transaction transferIn = transactionService.createTransaction(
                recipientWallet,
                TransactionType.TRANSFER_IN,
                TransactionStatus.SUCCESS,
                request.amount(),
                baseReferenceCode + "_IN",
                request.description()
        );

        // 8. Bidirectional link (set relatedTransactionId)
        transferOut.setRelatedTransactionId(transferIn.getId());
        transferIn.setRelatedTransactionId(transferOut.getId());

        // 9. Return TransferResponse
        return new TransferResponse(
                transferOut.getId(),
                senderWallet.getBalance().subtract(request.amount()),
                request.amount(),
                TransactionStatus.SUCCESS,
                LocalDateTime.now()
        );
    }

    private void validateTransfer(Long fromUserId, TransferRequest request) {
        User fromUser = userRepository.findById(fromUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));

        if (fromUser.getEmail().equals(request.toEmail())) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED);
        }

        User toUser = userRepository.findByEmail(request.toEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.RECIPIENT_NOT_FOUND));
        if (toUser.getStatus() == UserStatus.LOCKED) {
            throw new BusinessException(ErrorCode.RECIPIENT_LOCKED);
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }
    }
}
