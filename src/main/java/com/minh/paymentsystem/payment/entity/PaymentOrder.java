package com.minh.paymentsystem.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

import com.minh.paymentsystem.common.entity.AuditableEntity;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.user.entity.User;

@Entity
@Table(name = "payment_orders")
@Check(name = "ck_payment_orders_amount_positive", constraints = "amount > 0")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class PaymentOrder extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentOrderStatus status;

    @Column(name = "vnp_transaction_no")
    private String vnpTransactionNo;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
