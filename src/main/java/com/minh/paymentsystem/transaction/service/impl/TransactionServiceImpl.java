package com.minh.paymentsystem.transaction.service.impl;

import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.repository.TransactionRepository;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.wallet.entity.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

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
}
