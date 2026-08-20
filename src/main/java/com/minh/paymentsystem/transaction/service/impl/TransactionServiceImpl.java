package com.minh.paymentsystem.transaction.service.impl;

import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.dto.TransactionFilterRequest;
import com.minh.paymentsystem.transaction.dto.TransactionResponse;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.repository.TransactionRepository;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public Transaction createTransaction(Wallet wallet, TransactionType type, TransactionStatus status, BigDecimal amount, String referenceCode, String description) {
        log.info("Creating transaction for wallet {} with type {}, status {} and amount {}", wallet.getId(), type, status, amount);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .type(type)
                .status(status)
                .amount(amount)
                .referenceCode(referenceCode)
                .description(description)
                .build();

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getMyTransactions(Long userId, TransactionFilterRequest filter) {
        
        log.info("Fetching transactions for user id={} with filter={}", userId, filter);
        
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                filter.page(), 
                filter.size(), 
                Sort.by("createdAt").descending()
        );

        Page<Transaction> transactionPage = transactionRepository.findWithFilter(
                wallet.getId(),
                filter.type(),
                filter.status(),
                filter.fromDate(),
                filter.toDate(),
                pageable
        );

        List<TransactionResponse> content = transactionPage.getContent().stream()
                .map(TransactionResponse::from)
                .toList();

        return new PageResponse<>(
                content,
                transactionPage.getNumber(),
                transactionPage.getSize(),
                transactionPage.getTotalElements(),
                transactionPage.getTotalPages()
        );
    }
}
