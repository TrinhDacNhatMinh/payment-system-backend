package com.minh.paymentsystem.transaction.repository;

import com.minh.paymentsystem.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByReferenceCode(String referenceCode);
    Page<Transaction> findByWalletId(Long walletId, Pageable pageable);
}
