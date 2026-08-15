package com.minh.paymentsystem.wallet.service.impl;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.wallet.dto.WalletResponse;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import com.minh.paymentsystem.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public Wallet createWalletForUser(User user) {
        log.info("Creating wallet for user id={}", user.getId());
        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .currency("VND")
                .build();
        return walletRepository.save(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(Long userId) {
        log.info("Fetching wallet for user id={}", userId);
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        return WalletResponse.from(wallet);
    }

    @Override
    @Transactional
    public void debit(Long walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));

        walletRepository.save(wallet);
    }

    @Override
    @Transactional
    public void credit(Long walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        wallet.setBalance(wallet.getBalance().add(amount));

        walletRepository.save(wallet);
    }
}
