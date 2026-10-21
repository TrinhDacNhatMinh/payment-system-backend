package com.minh.paymentsystem.wallet.service;

import java.math.BigDecimal;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.validation.WholeVnd;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.wallet.dto.WalletResponse;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public Wallet createWalletForUser(User user) {
        log.debug("Creating wallet for user id={}", user.getId());
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
        log.debug("Fetching wallet for user id={}", userId);
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
        return WalletResponse.from(wallet);
    }

    @Override
    @Transactional
    public void debit(Long walletId, BigDecimal amount) {
        WholeVnd.requirePositiveAmount(amount);
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        WholeVnd.requireWritableBalance(wallet.getBalance());
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }

        wallet.setBalance(wallet.getBalance().subtract(amount));

        walletRepository.save(wallet);
    }

    @Override
    @Transactional
    public void credit(Long walletId, BigDecimal amount) {
        WholeVnd.requirePositiveAmount(amount);
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));

        WholeVnd.requireWritableBalance(wallet.getBalance());
        BigDecimal balance = wallet.getBalance().add(amount);
        WholeVnd.requireWritableBalance(balance);
        wallet.setBalance(balance);

        walletRepository.save(wallet);
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WALLET_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Wallet> findWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId);
    }
}
