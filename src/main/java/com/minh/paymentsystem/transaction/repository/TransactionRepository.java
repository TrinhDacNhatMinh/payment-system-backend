package com.minh.paymentsystem.transaction.repository;

import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByReferenceCode(String referenceCode);
    Page<Transaction> findByWalletId(Long walletId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.wallet.id = :walletId " +
           "AND (:type IS NULL OR t.type = :type) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (cast(:fromDate as timestamp) IS NULL OR t.createdAt >= :fromDate) " +
           "AND (cast(:toDate as timestamp) IS NULL OR t.createdAt <= :toDate)")
    Page<Transaction> findWithFilter(
            @Param("walletId") Long walletId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );
}
