package com.minh.paymentsystem.transfer;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.repository.TransactionRepository;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.service.TransferService;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
class TransferServiceConcurrencyTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        // Delete in correct FK order: transactions -> wallets -> users
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();

        // Create sender
        sender = User.builder()
                .email("sender@test.com")
                .password(passwordEncoder.encode("123456"))
                .fullName("Sender Test")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
        sender = userRepository.save(sender);

        Wallet senderWallet = Wallet.builder()
                .user(sender)
                .balance(new BigDecimal("10000.00"))
                .currency("VND")
                .build();
        walletRepository.save(senderWallet);

        // Create receiver
        receiver = User.builder()
                .email("receiver@test.com")
                .password(passwordEncoder.encode("123456"))
                .fullName("Receiver Test")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();
        receiver = userRepository.save(receiver);

        Wallet receiverWallet = Wallet.builder()
                .user(receiver)
                .balance(BigDecimal.ZERO)
                .currency("VND")
                .build();
        walletRepository.save(receiverWallet);
    }

    @AfterEach
    void tearDown() {
        // Delete in correct FK order: transactions -> wallets -> users
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void transfer_concurrentRequests_onlyOneSucceedsOrBothHandledCorrectly() throws InterruptedException {
        // given
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        TransferRequest request = new TransferRequest("receiver@test.com", new BigDecimal("10000.00"), "Concurrent Transfer");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // Wait for all threads to be ready

                    transferService.transfer(sender.getId(), request);
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.CONCURRENT_MODIFICATION) {
                        failCount.incrementAndGet();
                        log.warn("Caught expected BusinessException: {}", e.getMessage());
                    } else if (e.getErrorCode() == ErrorCode.WALLET_INSUFFICIENT_BALANCE) {
                        // Depending on DB isolation and timing, it might fail with INSUFFICIENT_BALANCE if it reads after the first one commits
                        failCount.incrementAndGet();
                        log.warn("Caught expected INSUFFICIENT_BALANCE: {}", e.getMessage());
                    }
                } catch (Exception e) {
                    boolean isConcurrencyError = false;
                    Throwable cause = e;
                    while (cause != null) {
                        if (cause instanceof org.springframework.dao.OptimisticLockingFailureException ||
                                cause instanceof jakarta.persistence.OptimisticLockException ||
                                cause.getClass().getName().contains("OptimisticLocking")) {
                            isConcurrencyError = true;
                            break;
                        }
                        cause = cause.getCause();
                    }

                    if (isConcurrencyError) {
                        failCount.incrementAndGet();
                        log.warn("Caught expected Optimistic Locking Exception: {}", e.getMessage());
                    } else {
                        log.error("Unexpected exception: ", e);
                        // Still increment failCount so we know it failed, but assertion on type of failure might be manual
                        failCount.incrementAndGet();
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(); // Wait until all threads are ready
        startLatch.countDown(); // Release the latch for all threads to start simultaneously
        doneLatch.await(); // Wait for all threads to finish
        executorService.shutdown();

        // then
        // 1. Only 1 request should succeed
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        // 2. Sender balance should be exactly 0 (10000-10000)
        Wallet finalSenderWallet = walletRepository.findByUserId(sender.getId()).orElseThrow();
        assertThat(finalSenderWallet.getBalance().compareTo(BigDecimal.ZERO)).isEqualTo(0);

        // 3. Receiver balance should be exactly 10000 (0 + 10000)
        Wallet finalReceiverWallet = walletRepository.findByUserId(receiver.getId()).orElseThrow();
        assertThat(finalReceiverWallet.getBalance().compareTo(new BigDecimal("10000.00"))).isEqualTo(0);
    }
}
