package com.minh.paymentsystem.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.auth.security.JwtTokenProvider;
import com.minh.paymentsystem.auth.security.RefreshTokenCookieProvider;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.dto.UserResponse;
import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.service.RefreshTokenService;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.wallet.service.WalletService;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private User user;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private String refreshToken;
    private RefreshToken validRefreshToken;

    @BeforeEach
    void setup() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .password("password123")
                .fullName("Test User")
                .phone("0123456789")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build();

        registerRequest = new RegisterRequest(
                "test@example.com",
                "password123",
                "Test User",
                "0123456789");

        loginRequest = new LoginRequest("test@example.com", "password123");

        refreshToken = "valid-refresh-token";

        validRefreshToken = RefreshToken.builder()
                .id(1L)
                .user(user)
                .token("valid-refresh-token")
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
    }

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        void register_emailAlreadyExists_throwsBusinessException() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(true);

            // when & then
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> authService.register(registerRequest));
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        @Test
        void register_emailAlreadyExists_doesNotSaveUser() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(true);

            // when
            assertThrows(BusinessException.class, () -> authService.register(registerRequest));

            // then
            verify(userService, never()).createUser(any());
        }

        @Test
        void register_emailAlreadyExists_doesNotCreateWallet() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(true);

            // when
            assertThrows(BusinessException.class, () -> authService.register(registerRequest));

            // then
            verify(walletService, never()).createWalletForUser(any());
        }

        @Test
        void register_validRequest_encodesPasswordBeforeSaving() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(false);
            when((passwordEncoder.encode(registerRequest.password()))).thenReturn("encodedPassword");

            // when
            authService.register(registerRequest);

            // then
            verify(userService, times(1)).createUser(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword()).isEqualTo("encodedPassword");
        }

        @Test
        void register_validRequest_assignsDefaultRoleUser() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(false);

            // when
            authService.register(registerRequest);

            // then
            verify(userService, times(1)).createUser(userCaptor.capture());
            assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);

        }

        @Test
        void register_validRequest_assignsDefaultStatusActive() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(false);

            // when
            authService.register(registerRequest);

            // then
            verify(userService, times(1)).createUser(userCaptor.capture());
            assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        void register_validRequest_savesUserToRepository() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(false);

            // when
            authService.register(registerRequest);

            // then
            verify(userService, times(1)).createUser(any(User.class));
        }

        @Test
        void register_validRequest_createsWalletForNewUser() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(false);

            // when
            authService.register(registerRequest);

            // then
            verify(walletService, times(1)).createWalletForUser(any(User.class));
        }

        @Test
        void register_validRequest_returnsUserResponseWithCorrectFields() {
            // given
            when(userService.existsByEmail(registerRequest.email())).thenReturn(false);

            // when
            UserResponse response = authService.register(registerRequest);

            // then
            assertThat(response.email()).isEqualTo(registerRequest.email());
            assertThat(response.fullName()).isEqualTo(registerRequest.fullName());
        }

    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        void login_invalidCredentials_throwsBusinessException() {
            // given
            when(userService.getUserByEmail(loginRequest.email()))
                    .thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(loginRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void login_userNotFound_returnsInvalidCredentials() {
            BusinessException original = new BusinessException(ErrorCode.USER_NOT_FOUND);
            when(userService.getUserByEmail(loginRequest.email()))
                    .thenThrow(original);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(loginRequest));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            assertThat(exception.getCause()).isSameAs(original);
            verifyNoInteractions(passwordEncoder, refreshTokenService, jwtTokenProvider);
        }

        @Test
        void login_otherBusinessError_propagatesOriginalException() {
            BusinessException original = new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            when(userService.getUserByEmail(loginRequest.email())).thenThrow(original);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(loginRequest));

            assertThat(exception).isSameAs(original);
            verifyNoInteractions(passwordEncoder, refreshTokenService, jwtTokenProvider);
        }

        @Test
        void login_accountLocked_throwsBusinessException() {
            // given
            user.setStatus(UserStatus.LOCKED);
            when(userService.getUserByEmail(loginRequest.email())).thenReturn(user);
            when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(true);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(loginRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
            verifyNoInteractions(refreshTokenService, jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void login_lockedAccountWithWrongPassword_returnsInvalidCredentials() {
            user.setStatus(UserStatus.LOCKED);
            when(userService.getUserByEmail(loginRequest.email())).thenReturn(user);
            when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(loginRequest));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            verifyNoInteractions(refreshTokenService, jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void login_wrongPassword_throwsBusinessException() {
            // given
            when(userService.getUserByEmail(loginRequest.email())).thenReturn(user);
            when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(false);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(loginRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @ParameterizedTest
        @EnumSource(Role.class)
        void login_validCredentials_returnsTokensAndRevokesOldTokens(Role role) {
            // given
            user.setRole(role);
            when(userService.getUserByEmail(loginRequest.email())).thenReturn(user);
            when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(true);

            when(jwtTokenProvider.generateAccessToken(user))
                    .thenReturn("new-access-token");
            when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh-token");
            when(refreshTokenCookieProvider.createCookie("new-refresh-token", Duration.ofDays(7))).thenReturn("login-cookie-header");
            when(jwtTokenProvider.getRefreshExpiration()).thenReturn(604800000L);
            when(jwtTokenProvider.getAccessExpiration()).thenReturn(3600000L); // 1 hour

            ArgumentCaptor<RefreshToken> newTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
            Instant beforeLogin = Instant.now();

            // when
            AuthResponse response = authService.login(loginRequest);

            // then
            verify(refreshTokenService, times(1)).revokeAllUserTokens(user);

            verify(refreshTokenService, times(1)).saveToken(newTokenCaptor.capture());
            assertThat(newTokenCaptor.getValue().getToken()).isEqualTo("new-refresh-token");

            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.cookie()).isEqualTo("login-cookie-header");
            verify(refreshTokenCookieProvider).createCookie("new-refresh-token", Duration.ofDays(7));
            assertThat(newTokenCaptor.getValue().getUser()).isSameAs(user);
            assertThat(newTokenCaptor.getValue().isRevoked()).isFalse();
            assertThat(newTokenCaptor.getValue().getExpiresAt()).isBetween(
                    beforeLogin.plus(Duration.ofDays(7)), Instant.now().plus(Duration.ofDays(7)));
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);
            assertSessionUser(response, role);
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        void shouldRejectMissingRefreshTokenBeforeLoadingToken() {
            BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(null));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING);
            verifyNoInteractions(refreshTokenService, jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void shouldRejectBlankRefreshTokenBeforeLoadingToken() {
            BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(" "));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING);
            verifyNoInteractions(refreshTokenService, jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void refresh_tokenNotFound_throwsBusinessException() {
            // given
            when(refreshTokenService.getToken(refreshToken))
                    .thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh(refreshToken));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        void refresh_tokenRevoked_throwsBusinessException() {
            // given
            validRefreshToken.setRevoked(true);
            when(refreshTokenService.getToken(refreshToken)).thenReturn(validRefreshToken);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh(refreshToken));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED);
            verify(refreshTokenService, never()).revokeAllUserTokens(any());
            verify(refreshTokenService, never()).saveToken(any());
            verifyNoInteractions(jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void refresh_tokenExpired_throwsBusinessException() {
            // given
            validRefreshToken.setExpiresAt(Instant.now().minusSeconds(10));
            when(refreshTokenService.getToken(refreshToken)).thenReturn(validRefreshToken);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.refresh(refreshToken));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);
            verify(refreshTokenService, never()).revokeAllUserTokens(any());
            verify(refreshTokenService, never()).saveToken(any());
            verifyNoInteractions(jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void refresh_lockedAccount_doesNotRevokeOrIssueTokens() {
            user.setStatus(UserStatus.LOCKED);
            when(refreshTokenService.getToken(refreshToken)).thenReturn(validRefreshToken);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.refresh(refreshToken));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
            verify(refreshTokenService, never()).revokeAllUserTokens(any());
            verify(refreshTokenService, never()).saveToken(any());
            verifyNoInteractions(jwtTokenProvider, refreshTokenCookieProvider);
        }

        @ParameterizedTest
        @EnumSource(Role.class)
        void refresh_validToken_revokesOldTokensAndReturnsNewPair(Role role) {
            // given
            user.setRole(role);
            when(refreshTokenService.getToken(refreshToken)).thenReturn(validRefreshToken);
            when(jwtTokenProvider.generateAccessToken(user))
                    .thenReturn("new-access-token");
            when(jwtTokenProvider.generateRefreshToken()).thenReturn("rotated-refresh-token");
            when(refreshTokenCookieProvider.createCookie("rotated-refresh-token", Duration.ofDays(7))).thenReturn("rotated-cookie-header");
            when(jwtTokenProvider.getRefreshExpiration()).thenReturn(604800000L);
            when(jwtTokenProvider.getAccessExpiration()).thenReturn(3600000L);
            Instant beforeRefresh = Instant.now();

            // when
            AuthResponse response = authService.refresh(refreshToken);

            // then
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.cookie()).isEqualTo("rotated-cookie-header");
            verify(refreshTokenCookieProvider).createCookie("rotated-refresh-token", Duration.ofDays(7));
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);
            assertSessionUser(response, role);
            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
            InOrder order = inOrder(refreshTokenService);
            order.verify(refreshTokenService).getToken(refreshToken);
            order.verify(refreshTokenService).revokeAllUserTokens(user);
            order.verify(refreshTokenService).saveToken(captor.capture());
            assertThat(captor.getValue().getUser()).isSameAs(user);
            assertThat(captor.getValue().getToken()).isEqualTo("rotated-refresh-token")
                    .isNotEqualTo(refreshToken);
            assertThat(captor.getValue().isRevoked()).isFalse();
            assertThat(captor.getValue().getExpiresAt()).isBetween(
                    beforeRefresh.plusMillis(604800000L), Instant.now().plusMillis(604800000L));
        }
    }

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        void shouldRejectMissingLogoutTokenBeforeLoadingToken() {
            BusinessException exception = assertThrows(BusinessException.class, () -> authService.logout(null));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING);
            verifyNoInteractions(refreshTokenService, jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void shouldRejectBlankLogoutTokenBeforeLoadingToken() {
            BusinessException exception = assertThrows(BusinessException.class, () -> authService.logout(" "));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_MISSING);
            verifyNoInteractions(refreshTokenService, jwtTokenProvider, refreshTokenCookieProvider);
        }

        @Test
        void logout_tokenNotFound_throwsBusinessException() {
            // given
            when(refreshTokenService.getToken(refreshToken))
                    .thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.logout(refreshToken));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        void logout_validToken_revokesAllUserTokens() {
            // given
            when(refreshTokenService.getToken(refreshToken)).thenReturn(validRefreshToken);
            when(refreshTokenCookieProvider.clearCookie()).thenReturn("cleared-cookie-header");

            // when
            String cookie = authService.logout(refreshToken);

            // then
            verify(refreshTokenService).revokeAllUserTokens(user);
            verify(refreshTokenService, never()).saveToken(any());
            assertThat(cookie).isEqualTo("cleared-cookie-header");
            verify(refreshTokenCookieProvider).clearCookie();
            verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        void logout_expiredToken_stillRevokesAllUserTokens() {
            validRefreshToken.setExpiresAt(Instant.now().minusSeconds(60));
            when(refreshTokenService.getToken(refreshToken)).thenReturn(validRefreshToken);

            authService.logout(refreshToken);

            verify(refreshTokenService).revokeAllUserTokens(user);
            verify(refreshTokenCookieProvider).clearCookie();
            verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        void logout_revokedToken_returnsRevokedError() {
            validRefreshToken.setRevoked(true);
            when(refreshTokenService.getToken(refreshToken)).thenReturn(validRefreshToken);

            BusinessException exception = assertThrows(BusinessException.class, () -> authService.logout(refreshToken));

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED);
            verify(refreshTokenService, never()).revokeAllUserTokens(any());
            verifyNoInteractions(jwtTokenProvider, refreshTokenCookieProvider);
        }
    }

    private void assertSessionUser(AuthResponse response, Role expectedRole) {
        assertThat(response.user().id()).isEqualTo(user.getId());
        assertThat(response.user().email()).isEqualTo(user.getEmail());
        assertThat(response.user().role()).isEqualTo(expectedRole);

        JsonNode json = new ObjectMapper().valueToTree(response);
        assertThat(json.size()).isEqualTo(4);
        assertThat(json.has("cookie")).isFalse();
        assertThat(json.has("refreshToken")).isFalse();
        assertThat(json.get("user").size()).isEqualTo(3);
        assertThat(json.get("user").get("id").longValue()).isEqualTo(user.getId());
        assertThat(json.get("user").get("email").textValue()).isEqualTo(user.getEmail());
        assertThat(json.get("user").get("role").textValue()).isEqualTo(expectedRole.name());
    }
}
