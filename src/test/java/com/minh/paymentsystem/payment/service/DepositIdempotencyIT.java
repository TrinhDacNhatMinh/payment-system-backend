package com.minh.paymentsystem.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.validation.Validator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.payment.dto.DepositOrderResponse;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.entity.PaymentOrder;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.repository.PaymentOrderRepository;
import com.minh.paymentsystem.transaction.repository.TransactionRepository;
import com.minh.paymentsystem.transaction.service.TransactionServiceImpl;
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
        PaymentServiceImpl.class,
        DepositOrderService.class,
        PaymentNotificationService.class,
        PaymentReturnUrl.class,
        UserServiceImpl.class,
        WalletServiceImpl.class,
        TransactionServiceImpl.class,
        DepositIdempotencyIT.TestConfig.class
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DepositIdempotencyIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Autowired
    private PaymentService service;
    @Autowired
    private PaymentOrderRepository orders;
    @Autowired
    private UserRepository users;
    @Autowired
    private WalletRepository wallets;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MutableClock clock;
    @MockitoBean
    private PaymentGatewayService gateway;
    @MockitoSpyBean
    private WalletServiceImpl walletService;
    @MockitoSpyBean
    private TransactionServiceImpl transactionService;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private User owner;
    private Wallet wallet;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE transfer_requests, transactions, wallets, refresh_tokens, payment_orders, users RESTART IDENTITY CASCADE");
        clock.now.set(Instant.parse("2026-10-22T05:00:00Z"));
        owner = users.save(user("deposit-owner@example.com"));
        wallet = wallets.save(Wallet.builder().user(owner).balance(BigDecimal.ZERO).build());
        when(gateway.buildPaymentUrl(anyString(), any(), anyString(), any(), any()))
                .thenAnswer(call -> "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?order=" + call.getArgument(0));
        when(gateway.verifyChecksum(any())).thenReturn(true);
        when(gateway.matchesMerchant(any())).thenReturn(true);
    }

    @AfterEach
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void shouldReplaySameOrderAndRejectMismatchedAmount() {
        DepositOrderResponse first = create();
        assertThat(create()).isEqualTo(first);
        BusinessException exception = assertThrows(BusinessException.class, () -> service.createDepositOrder(owner.getId(),
                List.of(KEY.toString()), new DepositRequest(new BigDecimal("20000")), "127.0.0.1"));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DEPOSIT_KEY_REUSED);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(service.getDepositRequest(owner.getId(), KEY.toString()).orderId()).isEqualTo(first.orderId());
        verify(gateway, times(1)).buildPaymentUrl(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void shouldCreateOneOrderForTwoConcurrentRequestsWithSameKey() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(gateway.buildPaymentUrl(anyString(), any(), anyString(), any(), any())).thenAnswer(call -> {
            barrier.await(3, TimeUnit.SECONDS);
            return "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?order=" + call.getArgument(0);
        });
        Future<DepositOrderResponse> first = executor.submit(this::create);
        Future<DepositOrderResponse> second = executor.submit(this::create);
        DepositOrderResponse winner = first.get(10, TimeUnit.SECONDS);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(winner);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void shouldScopeOrderAndKeyLookupToOwnerAndAllowSameKeyForAnotherUser() {
        DepositOrderResponse first = create();
        User other = users.save(user("other-deposit@example.com"));
        assertThat(assertThrows(BusinessException.class, () -> service.getOrder(other.getId(), first.orderId())).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_FOUND);
        assertThat(assertThrows(BusinessException.class, () -> service.getDepositRequest(other.getId(), KEY.toString())).getErrorCode())
                .isEqualTo(ErrorCode.DEPOSIT_REQUEST_NOT_FOUND);
        DepositOrderResponse second = service.createDepositOrder(other.getId(), List.of(KEY.toString()),
                new DepositRequest(new BigDecimal("10000")), "127.0.0.1");
        assertThat(second.orderId()).isNotEqualTo(first.orderId());
        assertThat(orders.count()).isEqualTo(2);
    }

    @Test
    void shouldCommitIpnOnceAndPreserveVerifiedMetadata() {
        DepositOrderResponse created = create();
        MultiValueMap<String, String> params = notification(created.orderId());
        assertThat(service.handleCallback(params).rspCode()).isEqualTo("00");
        assertThat(service.handleCallback(params).rspCode()).isEqualTo("02");
        params.set("vnp_ResponseCode", "24");
        params.set("vnp_TransactionStatus", "02");
        assertThat(service.handleCallback(params).rspCode()).isEqualTo("02");
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("10000");
        assertThat(transactions.count()).isEqualTo(1);
        PaymentOrder saved = orders.findByOrderId(created.orderId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentOrderStatus.SUCCESS);
        assertThat(saved.getPaidAt().toString()).isEqualTo("2026-10-22T12:00");
        assertThat(saved.getVnpTransactionNo()).isEqualTo("123456");
    }

    @Test
    void shouldRejectConcurrentIpnWhileOrderIsLockedWithoutDoubleCredit() throws Exception {
        DepositOrderResponse created = create();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release wallet credit");
            }
            return call.callRealMethod();
        }).when(walletService).credit(anyLong(), any());
        Future<String> first = executor.submit(() -> service.handleCallback(notification(created.orderId())).rspCode());
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.handleCallback(notification(created.orderId())).rspCode()).isEqualTo("99");
        } finally {
            release.countDown();
        }
        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("00");
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("10000");
        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void shouldRollbackCreditOrderAndMetadataWhenTransactionInsertFails() {
        DepositOrderResponse created = create();
        doThrow(new DataIntegrityViolationException("simulated transaction failure")).when(transactionService).createTransaction(any());
        assertThat(service.handleCallback(notification(created.orderId())).rspCode()).isEqualTo("99");
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");
        assertThat(transactions.count()).isZero();
        PaymentOrder pending = orders.findByOrderId(created.orderId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(pending.getPaidAt()).isNull();
        assertThat(pending.getVnpTransactionNo()).isNull();
        doCallRealMethod().when(transactionService).createTransaction(any());
        assertThat(service.handleCallback(notification(created.orderId())).rspCode()).isEqualTo("00");
    }

    @Test
    void shouldReturnBeforeIpnWithoutMarkingPaymentSuccessful() {
        DepositOrderResponse created = create();
        assertThat(service.processReturn(notification(created.orderId())).toString())
                .isEqualTo("http://localhost:5173/payment/result?orderId=" + created.orderId());
        assertThat(service.getOrder(owner.getId(), created.orderId()).status()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(transactions.count()).isZero();
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void shouldKeepExpiredLinkPendingAndAcceptVerifiedLateIpn() {
        DepositOrderResponse created = create();
        clock.now.set(clock.instant().plusSeconds(16 * 60));
        DepositOrderResponse replay = create();
        assertThat(replay.orderId()).isEqualTo(created.orderId());
        assertThat(replay.paymentUrlExpiresAt()).isEqualTo(created.paymentUrlExpiresAt());
        assertThat(replay.paymentUrl()).isNull();
        assertThat(replay.canContinuePayment()).isFalse();
        assertThat(replay.status()).isEqualTo(PaymentOrderStatus.PENDING);
        verify(gateway, times(1)).buildPaymentUrl(anyString(), any(), anyString(), any(), any());
        assertThat(service.handleCallback(notification(created.orderId())).rspCode()).isEqualTo("00");
    }

    @Test
    void shouldNotLeaveOrderWhenSigningFails() {
        when(gateway.buildPaymentUrl(anyString(), any(), anyString(), any(), any())).thenThrow(new IllegalStateException("sign failed"));
        assertThrows(IllegalStateException.class, this::create);
        assertThat(orders.count()).isZero();
    }

    @Test
    void shouldRollbackOnWalletOverflow() {
        jdbc.update("UPDATE wallets SET balance = 9999999999999999 WHERE id = ?", wallet.getId());
        DepositOrderResponse created = create();
        assertThat(service.handleCallback(notification(created.orderId())).rspCode()).isEqualTo("99");
        assertThat(service.getOrder(owner.getId(), created.orderId()).status()).isEqualTo(PaymentOrderStatus.PENDING);
        assertThat(transactions.count()).isZero();
        assertThat(wallets.findById(wallet.getId()).orElseThrow().getBalance()).isEqualByComparingTo("9999999999999999");
    }

    private DepositOrderResponse create() {
        return service.createDepositOrder(owner.getId(), List.of(KEY.toString()), new DepositRequest(new BigDecimal("10000")), "127.0.0.1");
    }

    private MultiValueMap<String, String> notification(String orderId) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>(PaymentServiceImplTest.successParameters());
        params.set("vnp_TxnRef", orderId);
        return params;
    }

    private User user(String email) {
        return User.builder().email(email).password("test-hash").fullName("Test User").role(Role.USER).status(UserStatus.ACTIVE).build();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        MutableClock paymentClock() {
            return new MutableClock();
        }

        @Bean
        Validator validator() {
            return new LocalValidatorFactoryBean();
        }
    }

    static class MutableClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-22T05:00:00Z"));

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
