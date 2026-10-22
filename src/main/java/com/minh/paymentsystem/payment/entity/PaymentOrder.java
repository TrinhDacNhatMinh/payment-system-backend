package com.minh.paymentsystem.payment.entity;

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
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

import com.minh.paymentsystem.common.entity.AuditableEntity;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.user.entity.User;

@Entity
@Table(name = "payment_orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_orders_deposit_key", columnNames = {
                "user_id",
                "operation",
                "idempotency_key"
        })
})
@Checks({
        @Check(name = "ck_payment_orders_amount_positive", constraints = "amount > 0"),
        @Check(name = "ck_payment_orders_whole_vnd", constraints = "amount = floor(amount)"),
        @Check(name = "ck_payment_orders_deposit_request", constraints = "(idempotency_key IS NULL AND operation IS NULL) OR (idempotency_key IS NOT NULL AND operation IS NOT NULL AND operation = 'DEPOSIT_CREATE' AND payment_url IS NOT NULL AND payment_url_created_at IS NOT NULL AND payment_url_expires_at IS NOT NULL AND payment_url_expires_at > payment_url_created_at)")
})
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PaymentOrder extends AuditableEntity {

    public static final String DEPOSIT_OPERATION = "DEPOSIT_CREATE";

    @Column(name = "operation", length = 32, updatable = false)
    private String operation;

    @Column(name = "idempotency_key", updatable = false)
    private UUID idempotencyKey;

    @Column(name = "payment_url", columnDefinition = "text", updatable = false)
    private String paymentUrl;

    @Column(name = "payment_url_created_at", updatable = false)
    private LocalDateTime paymentUrlCreatedAt;

    @Column(name = "payment_url_expires_at", updatable = false)
    private LocalDateTime paymentUrlExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private String orderId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentOrderStatus status;

    @Column(name = "vnp_transaction_no")
    private String vnpTransactionNo;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
