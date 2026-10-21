package com.minh.paymentsystem.transaction.entity;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

import com.minh.paymentsystem.common.entity.AuditableEntity;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.wallet.entity.Wallet;

@Entity
@Table(name = "transactions")
@Checks({
        @Check(name = "ck_transactions_amount_positive", constraints = "amount > 0"),
        @Check(name = "ck_transactions_whole_vnd", constraints = "amount = floor(amount)")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class Transaction extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "reference_code", nullable = false, unique = true, length = 64)
    private String referenceCode; // idempotency key

    @Column(name = "related_transaction_id")
    private Long relatedTransactionId; // link between TRANSFER_OUT and TRANSFER_IN

    @Column(name = "description")
    private String description;
}
