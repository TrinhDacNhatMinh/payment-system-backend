package com.minh.paymentsystem.wallet.entity;

import com.minh.paymentsystem.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Builder.Default
    @Column(precision = 18, scale = 2, nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(nullable = false)
    private String currency = "VND";

    @Version
    private Long version;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
