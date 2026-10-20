package com.minh.paymentsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.HttpCookie;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.security.cookie.RefreshTokenCookieProvider;
import com.minh.paymentsystem.auth.security.jwt.JwtTokenProvider;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.RefreshTokenRepository;
import com.minh.paymentsystem.user.repository.UserRepository;
import com.minh.paymentsystem.user.service.RefreshTokenServiceImpl;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.user.service.UserServiceImpl;
import com.minh.paymentsystem.wallet.service.WalletService;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.hikari.maximum-pool-size=6",
        "jwt.secret=auth-concurrency-test-secret-at-least-32-bytes",
        "jwt.access-expiration=1800000",
        "jwt.refresh-expiration=604800000",
        "auth.refresh-token-cookie.secure=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        AuthServiceImpl.class,
        UserServiceImpl.class,
        RefreshTokenServiceImpl.class,
        JwtTokenProvider.class,
        RefreshTokenCookieProvider.class,
        AuthSessionConcurrencyIT.PasswordConfig.class
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers
class AuthSessionConcurrencyIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AuthService authService;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository tokenRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private WalletService walletService;

    private TransactionTemplate transaction;
    private ExecutorService executor;
    private User user;
    private LoginRequest login;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        executor = Executors.newSingleThreadExecutor();
        user = createUser("concurrency@example.com");
        login = new LoginRequest(user.getEmail(), "password123");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        transaction.executeWithoutResult(status -> {
            tokenRepository.deleteAll();
            userRepository.deleteAll();
        });
    }

    @Test
    void shouldRejectSecondRefreshOfTheSameTokenAfterFirstCommits() throws Exception {
        String token = refreshToken(authService.login(login));
        Future<AuthResponse> waiting = startWaitingRequestAfter(() -> authService.refresh(token), () -> authService.refresh(token));

        assertWaitingFailure(waiting, ErrorCode.REFRESH_TOKEN_REVOKED);
        assertThat(tokenRepository.findAllByUserAndRevokedFalse(user)).hasSize(1);
    }

    @Test
    void shouldKeepOnlyOneActiveTokenWhenLoginsOverlap() throws Exception {
        Future<AuthResponse> waiting = startWaitingRequestAfter(() -> authService.login(login), () -> authService.login(login));

        String lastToken = refreshToken(waiting.get(10, TimeUnit.SECONDS));

        assertThat(tokenRepository.findAllByUserAndRevokedFalse(user))
                .extracting(token -> token.getToken())
                .containsExactly(lastToken);
    }

    @Test
    void shouldRejectOldRefreshWhenLoginCommitsWhileRefreshWaits() throws Exception {
        String token = refreshToken(authService.login(login));
        Future<AuthResponse> waiting = startWaitingRequestAfter(() -> authService.login(login), () -> authService.refresh(token));

        assertWaitingFailure(waiting, ErrorCode.REFRESH_TOKEN_REVOKED);
        assertThat(tokenRepository.findAllByUserAndRevokedFalse(user)).hasSize(1);
    }

    @Test
    void shouldRejectRefreshWhenLogoutCommitsWhileRefreshWaits() throws Exception {
        String token = refreshToken(authService.login(login));
        Future<AuthResponse> waiting = startWaitingRequestAfter(() -> authService.logout(token), () -> authService.refresh(token));

        assertWaitingFailure(waiting, ErrorCode.REFRESH_TOKEN_REVOKED);
        assertThat(tokenRepository.findAllByUserAndRevokedFalse(user)).isEmpty();
    }

    @Test
    void shouldNotRevokeNewTokenWhenLogoutWithOldTokenWaitsForRefresh() throws Exception {
        String token = refreshToken(authService.login(login));
        Future<String> waiting = startWaitingRequestAfter(() -> authService.refresh(token), () -> authService.logout(token));

        assertThat(HttpCookie.parse(waiting.get(10, TimeUnit.SECONDS)).get(0).getMaxAge()).isZero();
        assertThat(tokenRepository.findAllByUserAndRevokedFalse(user)).hasSize(1);
        assertThat(tokenRepository.findStateByToken(token).orElseThrow().revoked()).isTrue();
    }

    @Test
    void shouldAllowAnotherUserToLoginWhileFirstUserIsLocked() {
        User other = createUser("other@example.com");

        transaction.executeWithoutResult(status -> {
            userService.getUserByIdForSessionUpdate(user.getId());
            CompletableFuture<AuthResponse> independent = CompletableFuture.supplyAsync(
                    () -> authService.login(new LoginRequest(other.getEmail(), "password123")), executor);
            await().atMost(3, TimeUnit.SECONDS).until(independent::isDone);
            assertThat(independent.join().user().id()).isEqualTo(other.getId());
        });
    }

    @Test
    void shouldRefreshCachedUserAfterWaitingForSessionLock() {
        String token = refreshToken(authService.login(login));
        Future<AuthResponse> waiting = startWaitingRequestAfter(() -> userService.lockUser(user.getId()),
                () -> transaction.execute(status -> {
                    User cached = userRepository.findById(user.getId()).orElseThrow();
                    assertThat(cached.getStatus()).isEqualTo(UserStatus.ACTIVE);
                    return authService.refresh(token);
                }));

        assertWaitingFailure(waiting, ErrorCode.ACCOUNT_LOCKED);
    }

    @Test
    void shouldReturnSessionBusyWhenLockQueryTimesOutWithoutIssuingTokens() {
        transaction.executeWithoutResult(status -> {
            userService.getUserByIdForSessionUpdate(user.getId());
            Future<AuthResponse> waiting = executor.submit(() -> authService.login(login));

            assertWaitingFailure(waiting, ErrorCode.AUTH_SESSION_BUSY);

            assertThat(tokenRepository.findAllByUserAndRevokedFalse(user)).isEmpty();
        });
    }

    private <T> Future<T> startWaitingRequestAfter(Runnable firstOperation, Callable<T> waitingOperation) {
        return transaction.execute(status -> {
            userService.getUserByIdForSessionUpdate(user.getId());
            firstOperation.run();
            Future<T> waiting = executor.submit(waitingOperation);
            // Verify a real PostgreSQL lock wait before allowing the first transaction to commit.
            await().atMost(3, TimeUnit.SECONDS).until(() -> {
                jdbcTemplate.execute("SELECT pg_stat_clear_snapshot()");
                return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND wait_event_type = 'Lock' AND query LIKE '%users%')",
                        Boolean.class));
            });
            return waiting;
        });
    }

    private void assertWaitingFailure(Future<?> waiting, ErrorCode errorCode) {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> waiting.get(10, TimeUnit.SECONDS));
        assertThat(exception.getCause()).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo(errorCode));
    }

    private User createUser(String email) {
        return transaction.execute(status -> userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .fullName("Concurrency Test")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build()));
    }

    private String refreshToken(AuthResponse response) {
        return HttpCookie.parse(response.cookie()).get(0).getValue();
    }

    @TestConfiguration
    static class PasswordConfig {

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
