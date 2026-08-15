package com.minh.paymentsystem.wallet.service;

import java.math.BigDecimal;

import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.wallet.dto.WalletResponse;
import com.minh.paymentsystem.wallet.entity.Wallet;

public interface WalletService {
    Wallet createWalletForUser(User user);

    WalletResponse getMyWallet(Long userId);

    void debit(Long walletId, BigDecimal amount);

    void credit(Long walletId, BigDecimal amount);
}
