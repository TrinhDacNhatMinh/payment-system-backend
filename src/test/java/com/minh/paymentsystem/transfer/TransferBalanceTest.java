package com.minh.paymentsystem.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.service.TransferService;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import com.minh.paymentsystem.wallet.service.WalletService;

@SpringBootTest
@Transactional
class TransferBalanceTest {

    @Autowired
    private TransferService transferService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldReturnThePersistedSenderBalanceAfterTransfer() {
        User sender = createUser("balance-sender@test.com");
        User recipient = createUser("balance-recipient@test.com");
        try {
            walletRepository.save(Wallet.builder()
                    .user(sender)
                    .balance(new BigDecimal("500000"))
                    .build());
            walletRepository.save(Wallet.builder()
                    .user(recipient)
                    .balance(BigDecimal.ZERO)
                    .build());

            TransferResponse response = transferService.transfer(sender.getId(), List.of(UUID.randomUUID().toString()),
                    new TransferRequest(recipient.getEmail(), new BigDecimal("100000"), "Balance regression"));

            Wallet persistedSender = walletRepository.findByUserId(sender.getId()).orElseThrow();
            Wallet persistedRecipient = walletRepository.findByUserId(recipient.getId()).orElseThrow();
            assertThat(response.fromWalletBalance()).isEqualByComparingTo("400000");
            assertThat(response.fromWalletBalance()).isEqualByComparingTo(persistedSender.getBalance());
            assertThat(persistedRecipient.getBalance()).isEqualByComparingTo("100000");
        } finally {
            jdbc.update("DELETE FROM transfer_requests WHERE user_id IN (?, ?)", sender.getId(), recipient.getId());
            jdbc.update("DELETE FROM transactions WHERE wallet_id IN (SELECT id FROM wallets WHERE user_id IN (?, ?))", sender.getId(), recipient.getId());
            jdbc.update("DELETE FROM wallets WHERE user_id IN (?, ?)", sender.getId(), recipient.getId());
            jdbc.update("DELETE FROM users WHERE id IN (?, ?)", sender.getId(), recipient.getId());
        }
    }

    @Test
    void shouldLoadWalletUserLazilyAndMapWalletResponseWithinTransaction() {
        User owner = createUser("lazy-wallet@test.com");
        Long ownerId = owner.getId();
        Wallet wallet = walletRepository.save(Wallet.builder()
                .user(owner)
                .balance(new BigDecimal("500000"))
                .build());
        Long walletId = wallet.getId();
        entityManager.flush();
        entityManager.clear();

        Wallet loaded = walletRepository.findById(walletId).orElseThrow();

        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(loaded, "user")).isFalse();
        assertThat(walletService.getMyWallet(ownerId).userId()).isEqualTo(ownerId);
    }

    private User createUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName("Test User")
                .password("unused-test-password")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }
}
