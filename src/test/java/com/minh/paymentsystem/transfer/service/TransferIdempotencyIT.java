package com.minh.paymentsystem.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.validation.Validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.repository.TransactionRepository;
import com.minh.paymentsystem.transaction.service.TransactionServiceImpl;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.transfer.exception.TransferRejectedException;
import com.minh.paymentsystem.transfer.repository.TransferInstructionRepository;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.user.service.UserServiceImpl;
import com.minh.paymentsystem.wallet.entity.Wallet;
import com.minh.paymentsystem.wallet.repository.WalletRepository;
import com.minh.paymentsystem.wallet.service.WalletServiceImpl;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.maximum-pool-size=8"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        TransferServiceImpl.class,
        TransferRegistrationService.class,
        TransferProcessingService.class,
        UserServiceImpl.class,
        WalletServiceImpl.class,
        TransactionServiceImpl.class,
        TransferIdempotencyIT.TestConfig.class
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransferIdempotencyIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private TransferService transferService;
    @Autowired
    private TransferRegistrationService registrationService;
    @Autowired
    private TransferInstructionRepository instructionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private WalletServiceImpl walletService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private User sender;
    private User recipient;
    private Wallet senderWallet;
    private TransferRequest request;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE transfer_requests, transactions, wallets, refresh_tokens, payment_orders, users RESTART IDENTITY CASCADE");
        sender = userRepository.save(user("sender@example.com"));
        recipient = userRepository.save(user("recipient@example.com"));
        senderWallet = walletRepository.save(Wallet.builder().user(sender).balance(new BigDecimal("10000.00")).build());
        walletRepository.save(Wallet.builder().user(recipient).balance(BigDecimal.ZERO).build());
        request = new TransferRequest(recipient.getEmail(), new BigDecimal("1000.50"), "  ");
    }

    @AfterEach
    void shutDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldReplayAfterResponseLossWithoutChangingMoneyAgain() {
        UUID key = UUID.randomUUID();
        TransferResponse original = submit(sender, key, request);
        // Discarding the original response models loss after the commit; GET and retry read persisted state.
        TransferResponse restored = transferService.getRequest(sender.getId(), key.toString()).result();
        TransferResponse replay = submit(sender, key,
                new TransferRequest(" recipient@example.com ", new BigDecimal("1000.5"), "  "));

        assertThat(restored).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(original);
        assertThat(replay).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class).isEqualTo(restored);
        assertMoney("8999.50", "1000.50", 2);
    }

    @Test
    void shouldRejectEachDifferentPayloadWithoutChangingOriginal() {
        UUID key = UUID.randomUUID();
        TransferResponse original = submit(sender, key, request);

        assertMismatch(key, new TransferRequest(request.toEmail(), new BigDecimal("1001.50"), request.description()));
        assertMismatch(key, new TransferRequest("other@example.com", request.amount(), request.description()));
        assertMismatch(key, new TransferRequest(request.toEmail(), request.amount(), null));
        assertThat(transferService.getRequest(sender.getId(), key.toString()).result().transactionId()).isEqualTo(original.transactionId());
        assertMoney("8999.50", "1000.50", 2);
    }

    @Test
    void shouldReplayRejectedRequestEvenAfterBalanceChanges() {
        UUID key = UUID.randomUUID();
        TransferRequest tooLarge = new TransferRequest(recipient.getEmail(), new BigDecimal("11000"), null);

        TransferRejectedException original = assertThrows(TransferRejectedException.class, () -> submit(sender, key, tooLarge));
        walletService.credit(senderWallet.getId(), new BigDecimal("5000"));
        TransferRejectedException replay = assertThrows(TransferRejectedException.class, () -> submit(sender, key, tooLarge));

        assertThat(replay.getError()).isEqualTo(original.getError());
        assertThat(transferService.getRequest(sender.getId(), key.toString()).state()).isEqualTo(TransferRequestState.REJECTED);
        assertMoney("15000", "0", 0);
    }

    @Test
    void shouldResumePendingRegistrationWithoutANewKey() {
        UUID key = UUID.randomUUID();
        registrationService.register(sender.getId(), key, request);

        assertThat(transferService.getRequest(sender.getId(), key.toString()).state()).isEqualTo(TransferRequestState.PENDING);
        assertMoney("10000", "0", 0);
        submit(sender, key, request);
        assertMoney("8999.50", "1000.50", 2);
    }

    @Test
    void shouldRollbackDebitWhenCreditFailsAndPermitManualRetry() {
        UUID key = UUID.randomUUID();
        Long recipientWalletId = walletRepository.findByUserId(recipient.getId()).orElseThrow().getId();
        doThrow(new IllegalStateException("simulated worker failure")).when(walletService).credit(recipientWalletId, request.amount());

        assertThrows(IllegalStateException.class, () -> submit(sender, key, request));

        assertThat(transferService.getRequest(sender.getId(), key.toString()).state()).isEqualTo(TransferRequestState.PENDING);
        assertMoney("10000", "0", 0);
        reset(walletService);
        submit(sender, key, request);
        assertMoney("8999.50", "1000.50", 2);
    }

    @Test
    void shouldRollbackMoneyAndTransactionsWhenReceiptCannotCommit() {
        UUID key = UUID.randomUUID();
        jdbc.execute("CREATE FUNCTION reject_test_receipt() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.state = 'SUCCEEDED' THEN RAISE EXCEPTION 'simulated receipt commit failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER reject_test_receipt BEFORE UPDATE ON transfer_requests FOR EACH ROW EXECUTE FUNCTION reject_test_receipt()");
        try {
            assertThrows(RuntimeException.class, () -> submit(sender, key, request));
            assertThat(transferService.getRequest(sender.getId(), key.toString()).state()).isEqualTo(TransferRequestState.PENDING);
            assertMoney("10000", "0", 0);
        } finally {
            jdbc.execute("DROP TRIGGER reject_test_receipt ON transfer_requests");
            jdbc.execute("DROP FUNCTION reject_test_receipt()");
        }
        submit(sender, key, request);
        assertMoney("8999.50", "1000.50", 2);
    }

    @Test
    void shouldReturnInProgressWhileFirstWorkerOwnsLockAndAllowNonblockingGet() throws Exception {
        UUID key = UUID.randomUUID();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test worker was not released");
            }
            return invocation.callRealMethod();
        }).when(walletService).debit(senderWallet.getId(), request.amount());

        Future<TransferResponse> first = executor.submit(() -> submit(sender, key, request));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            BusinessException busy = assertThrows(BusinessException.class, () -> submit(sender, key, request));
            assertThat(busy.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_IN_PROGRESS);
            assertThat(transferService.getRequest(sender.getId(), key.toString()).state()).isEqualTo(TransferRequestState.PENDING);
            assertMismatch(key, new TransferRequest(request.toEmail(), new BigDecimal("1001"), request.description()));
        } finally {
            release.countDown();
        }
        TransferResponse completed = first.get(10, TimeUnit.SECONDS);
        assertThat(submit(sender, key, request).transactionId()).isEqualTo(completed.transactionId());
        assertMoney("8999.50", "1000.50", 2);
    }

    @Test
    void shouldHandleConcurrentRegistrationAndExecuteOnlyOnce() throws Exception {
        UUID key = UUID.randomUUID();
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Object> command = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                return submit(sender, key, request);
            } catch (BusinessException exception) {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_IN_PROGRESS);
                return exception;
            }
        };
        Future<Object> first = executor.submit(command);
        Future<Object> second = executor.submit(command);
        List<Object> responses = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));

        assertThat(responses.stream().filter(TransferResponse.class::isInstance).count()).isGreaterThanOrEqualTo(1);
        Long transactionId = transferService.getRequest(sender.getId(), key.toString()).result().transactionId();
        assertThat(responses.stream().filter(TransferResponse.class::isInstance).map(TransferResponse.class::cast)
                .map(TransferResponse::transactionId).toList()).containsOnly(transactionId);
        assertThat(instructionRepository.count()).isEqualTo(1);
        assertMoney("8999.50", "1000.50", 2);
    }

    @Test
    void shouldRetainWalletVersionChecksForDifferentKeys() throws Exception {
        UUID firstKey = UUID.randomUUID();
        UUID secondKey = UUID.randomUUID();
        CyclicBarrier bothPrepared = new CyclicBarrier(2);
        doAnswer(invocation -> {
            bothPrepared.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(walletService).debit(senderWallet.getId(), request.amount());
        AtomicInteger successes = new AtomicInteger();
        Callable<Void> firstCommand = () -> {
            submitWithPossibleConflict(firstKey, successes);
            return null;
        };
        Callable<Void> secondCommand = () -> {
            submitWithPossibleConflict(secondKey, successes);
            return null;
        };
        Future<Void> first = executor.submit(firstCommand);
        Future<Void> second = executor.submit(secondCommand);
        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);

        assertThat(successes.get()).isEqualTo(1);
        assertMoney("8999.50", "1000.50", 2);
        UUID pendingKey = transferService.getRequest(sender.getId(), firstKey.toString()).state() == TransferRequestState.PENDING
                ? firstKey
                : secondKey;
        reset(walletService);
        submit(sender, pendingKey, request);
        assertMoney("7999.00", "2001.00", 4);
    }

    @Test
    void shouldKeepSameUuidIndependentAcrossUsersAndHideOtherOwners() {
        UUID key = UUID.randomUUID();
        submit(sender, key, request);
        BusinessException hidden = assertThrows(BusinessException.class,
                () -> transferService.getRequest(recipient.getId(), key.toString()));
        assertThat(hidden.getErrorCode()).isEqualTo(ErrorCode.TRANSFER_REQUEST_NOT_FOUND);

        TransferResponse reverse = submit(recipient, key,
                new TransferRequest(sender.getEmail(), request.amount(), null));

        assertThat(reverse.transactionId()).isNotEqualTo(transferService.getRequest(sender.getId(), key.toString()).result().transactionId());
        assertThat(instructionRepository.count()).isEqualTo(2);
        assertMoney("10000", "0", 4);
    }

    private void submitWithPossibleConflict(UUID key, AtomicInteger successes) {
        try {
            submit(sender, key, request);
            successes.incrementAndGet();
        } catch (OptimisticLockingFailureException exception) {
            assertThat(transferService.getRequest(sender.getId(), key.toString()).state()).isEqualTo(TransferRequestState.PENDING);
        }
    }

    private TransferResponse submit(User user, UUID key, TransferRequest payload) {
        return transferService.transfer(user.getId(), List.of(key.toString()), payload);
    }

    private void assertMismatch(UUID key, TransferRequest payload) {
        BusinessException exception = assertThrows(BusinessException.class, () -> submit(sender, key, payload));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    private void assertMoney(String senderBalance, String recipientBalance, long transactions) {
        assertThat(walletRepository.findByUserId(sender.getId()).orElseThrow().getBalance()).isEqualByComparingTo(senderBalance);
        assertThat(walletRepository.findByUserId(recipient.getId()).orElseThrow().getBalance()).isEqualByComparingTo(recipientBalance);
        assertThat(transactionRepository.count()).isEqualTo(transactions);
    }

    private User user(String email) {
        return User.builder().email(email).password("unused-test-password").fullName("Transfer Test")
                .role(Role.USER).status(UserStatus.ACTIVE).build();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        Validator validator() {
            return new LocalValidatorFactoryBean();
        }
    }
}
