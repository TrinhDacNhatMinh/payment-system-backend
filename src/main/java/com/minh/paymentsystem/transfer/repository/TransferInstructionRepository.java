package com.minh.paymentsystem.transfer.repository;

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

import com.minh.paymentsystem.transfer.entity.TransferInstruction;

@Repository
public interface TransferInstructionRepository extends JpaRepository<TransferInstruction, Long> {

    Optional<TransferInstruction> findByUserIdAndOperationAndIdempotencyKey(Long userId, String operation, UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT r FROM TransferInstruction r WHERE r.id = :id")
    Optional<TransferInstruction> findByIdForUpdate(@Param("id") Long id);
}
