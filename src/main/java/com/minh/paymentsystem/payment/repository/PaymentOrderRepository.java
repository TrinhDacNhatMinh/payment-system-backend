package com.minh.paymentsystem.payment.repository;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.minh.paymentsystem.payment.entity.PaymentOrder;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByOrderId(String orderId);

    Optional<PaymentOrder> findByOrderIdAndUserId(String orderId, Long userId);

    Optional<PaymentOrder> findByUserIdAndOperationAndIdempotencyKey(Long userId, String operation, UUID key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT p FROM PaymentOrder p WHERE p.orderId = :orderId")
    Optional<PaymentOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
