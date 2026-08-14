package com.minh.paymentsystem.transaction.service;

import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.wallet.entity.Wallet;

import java.math.BigDecimal;

public interface TransactionService {
    Transaction createTransaction(Wallet wallet, TransactionType type, TransactionStatus status, BigDecimal amount, String referenceCode, String description);
}
