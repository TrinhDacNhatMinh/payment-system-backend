package com.minh.paymentsystem.transfer.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Check;

import com.minh.paymentsystem.common.entity.AuditableEntity;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.entity.Transaction;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.user.entity.User;

@Entity
@Table(name = "transfer_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_transfer_requests_scope", columnNames = {
                "user_id",
                "operation",
                "idempotency_key"
        })
})
@Check(name = "ck_transfer_requests_state", constraints = "(state = 'PENDING' AND transaction_id IS NULL AND receipt_balance IS NULL AND receipt_amount IS NULL AND receipt_status IS NULL AND receipt_created_at IS NULL AND error_http_status IS NULL AND error_code IS NULL AND error_message IS NULL) OR "
        + "(state = 'SUCCEEDED' AND transaction_id IS NOT NULL AND receipt_balance IS NOT NULL AND receipt_balance >= 0 AND receipt_amount IS NOT NULL AND receipt_amount = amount AND receipt_status IS NOT NULL AND receipt_status = 'SUCCESS' AND receipt_created_at IS NOT NULL AND error_http_status IS NULL AND error_code IS NULL AND error_message IS NULL) OR "
        + "(state = 'REJECTED' AND transaction_id IS NULL AND receipt_balance IS NULL AND receipt_amount IS NULL AND receipt_status IS NULL AND receipt_created_at IS NULL AND error_http_status IS NOT NULL AND error_http_status BETWEEN 400 AND 499 AND error_code IS NOT NULL AND error_message IS NOT NULL)")
@Getter
@SuperBuilder
@NoArgsConstructor
public class TransferInstruction extends AuditableEntity {

    public static final String OPERATION = "TRANSFER";
    public static final String PAYLOAD_SCHEMA = "TRANSFER_V1";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "operation", nullable = false, length = 16, updatable = false)
    private String operation;

    @Column(name = "payload_schema", nullable = false, length = 32, updatable = false)
    private String payloadSchema;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Column(name = "to_email", nullable = false, length = 255, updatable = false)
    private String toEmail;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "payload_fingerprint", nullable = false, length = 64, updatable = false)
    private String payloadFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private TransferRequestState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", unique = true)
    private Transaction transaction;

    @Column(name = "receipt_balance", precision = 18, scale = 2)
    private BigDecimal receiptBalance;

    @Column(name = "receipt_amount", precision = 18, scale = 2)
    private BigDecimal receiptAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", length = 16)
    private TransactionStatus receiptStatus;

    @Column(name = "receipt_created_at")
    private LocalDateTime receiptCreatedAt;

    @Column(name = "error_http_status")
    private Integer errorHttpStatus;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 255)
    private String errorMessage;

    public void markSucceeded(Transaction outgoing, BigDecimal balance, LocalDateTime completedAt) {
        requirePending();
        transaction = outgoing;
        receiptBalance = balance;
        receiptAmount = amount;
        receiptStatus = TransactionStatus.SUCCESS;
        receiptCreatedAt = completedAt;
        state = TransferRequestState.SUCCEEDED;
    }

    public void markRejected(ErrorCode rejection) {
        requirePending();
        errorHttpStatus = rejection.getHttpStatus().value();
        errorCode = rejection.name();
        errorMessage = rejection.getMessage();
        state = TransferRequestState.REJECTED;
    }

    private void requirePending() {
        if (state != TransferRequestState.PENDING) {
            throw new IllegalStateException("Transfer outcome is immutable");
        }
    }
}
