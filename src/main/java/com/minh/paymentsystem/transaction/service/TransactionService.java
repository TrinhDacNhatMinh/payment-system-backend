package com.minh.paymentsystem.transaction.service;

import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.dto.TransactionFilterRequest;
import com.minh.paymentsystem.transaction.dto.TransactionResponse;
import com.minh.paymentsystem.transaction.entity.Transaction;

public interface TransactionService {
    Transaction createTransaction(CreateTransactionCommand command);

    PageResponse<TransactionResponse> getMyTransactions(Long userId, TransactionFilterRequest filter);
}
