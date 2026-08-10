package com.minh.paymentsystem.wallet.service;

import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.wallet.dto.WalletResponse;
import com.minh.paymentsystem.wallet.entity.Wallet;

public interface WalletService {
    Wallet createWalletForUser(User user);
    WalletResponse getMyWallet(Long userId);
}
