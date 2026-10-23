package com.minh.paymentsystem.transaction.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.validation.WholeVnd;
import com.minh.paymentsystem.transaction.dto.CreateTransactionCommand;
import com.minh.paymentsystem.transaction.dto.TransactionFilterRequest;
import com.minh.paymentsystem.transaction.dto.TransactionResponse;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.repository.TransactionRepository;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.service.WalletService;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletService walletService;

    @Override
    @Transactional
    public Transaction createTransaction(CreateTransactionCommand command) {
        WholeVnd.requirePositiveAmount(command.amount());
        log.debug("Creating transaction for wallet {} with type {}, status {} and amount {}", command.wallet().getId(), command.type(), command.status(), command.amount());

        Transaction transaction = Transaction.builder()
                .wallet(command.wallet())
                .type(command.type())
                .status(command.status())
                .amount(command.amount())
                .referenceCode(command.referenceCode())
                .description(command.description())
                .build();

        return transactionRepository.save(transaction);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getMyTransactions(Long userId, TransactionFilterRequest filter) {

        log.debug("Fetching transactions: userId={}, type={}, status={}, fromDate={}, toDate={}, page={}, size={}",
                userId, filter.type(), filter.status(), filter.fromDate(), filter.toDate(), filter.page(), filter.size());

        Wallet wallet = walletService.getWalletByUserId(userId);

        Pageable pageable = PageRequest.of(
                filter.page(),
                filter.size(),
                Sort.by("createdAt").descending());

        Page<Transaction> transactionPage = transactionRepository.findWithFilter(
                wallet.getId(),
                filter.type(),
                filter.status(),
                filter.fromDate(),
                filter.toDate(),
                pageable);

        return toPageResponse(transactionPage);
    }

    private PageResponse<TransactionResponse> toPageResponse(Page<Transaction> transactionPage) {
        List<TransactionResponse> content = transactionPage.getContent()
                .stream()
                .map(TransactionResponse::from)
                .toList();

        return new PageResponse<>(
                content,
                transactionPage.getNumber(),
                transactionPage.getSize(),
                transactionPage.getTotalElements(),
                transactionPage.getTotalPages());
    }
}
