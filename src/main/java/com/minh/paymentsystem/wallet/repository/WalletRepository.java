package com.minh.paymentsystem.wallet.repository;

import com.minh.paymentsystem.wallet.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);
    Optional<Wallet> findByUserEmail(String email);
}
