package com.minh.paymentsystem.auth.service;

import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.LoginResponse;
import com.minh.paymentsystem.auth.dto.RefreshTokenRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.auth.security.JwtTokenProvider;
import com.minh.paymentsystem.auth.service.impl.AuthServiceImpl;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.dto.UserResponse;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.service.RefreshTokenService;
import com.minh.paymentsystem.user.service.UserService;
import com.minh.paymentsystem.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private WalletService walletService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private User user;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private RefreshTokenRequest refreshTokenRequest;
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
                "0123456789"
        );

        loginRequest = new LoginRequest("test@example.com", "password123");
        
        refreshTokenRequest = new RefreshTokenRequest("valid-refresh-token");
        
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
            BusinessException exception = assertThrows(BusinessException.class, () -> authService.register(registerRequest));
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
            when(userService.getUserByEmail(loginRequest.email())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(loginRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        }

        @Test
        void login_accountLocked_throwsBusinessException() {
            // given
            User lockedUser = User.builder().status(UserStatus.LOCKED).build();
            when(userService.getUserByEmail(loginRequest.email())).thenReturn(lockedUser);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(loginRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
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

        @Test
        void login_validCredentials_returnsTokensAndRevokesOldTokens() {
            // given
            when(userService.getUserByEmail(loginRequest.email())).thenReturn(user);
            when(passwordEncoder.matches(loginRequest.password(), user.getPassword())).thenReturn(true);
            
            
            when(jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole())).thenReturn("new-access-token");
            when(jwtTokenProvider.generateRefreshToken()).thenReturn("new-refresh-token");
            when(jwtTokenProvider.getAccessExpiration()).thenReturn(3600000L); // 1 hour

            ArgumentCaptor<RefreshToken> newTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);

            // when
            LoginResponse response = authService.login(loginRequest);

            // then
            verify(refreshTokenService, times(1)).revokeAllUserTokens(user);
            
            verify(refreshTokenService, times(1)).saveToken(newTokenCaptor.capture());
            assertThat(newTokenCaptor.getValue().getToken()).isEqualTo("new-refresh-token");
            
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        }
    }

    @Nested
    @DisplayName("refresh()")
    class RefreshTests {

        @Test
        void refresh_tokenNotFound_throwsBusinessException() {
            // given
            when(refreshTokenService.getToken(refreshTokenRequest.refreshToken())).thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh(refreshTokenRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        void refresh_tokenRevoked_throwsBusinessException() {
            // given
            validRefreshToken.setRevoked(true);
            when(refreshTokenService.getToken(refreshTokenRequest.refreshToken())).thenReturn(validRefreshToken);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh(refreshTokenRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_REVOKED);
        }

        @Test
        void refresh_tokenExpired_throwsBusinessException() {
            // given
            validRefreshToken.setExpiresAt(Instant.now().minusSeconds(10));
            when(refreshTokenService.getToken(refreshTokenRequest.refreshToken())).thenReturn(validRefreshToken);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.refresh(refreshTokenRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        @Test
        void refresh_validToken_returnsNewToken() {
            // given
            when(refreshTokenService.getToken(refreshTokenRequest.refreshToken())).thenReturn(validRefreshToken);
            when(jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole())).thenReturn("new-access-token");
            when(jwtTokenProvider.getAccessExpiration()).thenReturn(3600000L);

            // when
            LoginResponse response = authService.refresh(refreshTokenRequest);

            // then
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.refreshToken()).isEqualTo(refreshTokenRequest.refreshToken());
        }
    }

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        void logout_tokenNotFound_throwsBusinessException() {
            // given
            when(refreshTokenService.getToken(refreshTokenRequest.refreshToken())).thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> authService.logout(refreshTokenRequest));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        @Test
        void logout_validToken_revokesToken() {
            // given
            when(refreshTokenService.getToken(refreshTokenRequest.refreshToken())).thenReturn(validRefreshToken);

            // when
            authService.logout(refreshTokenRequest);

            // then
            assertThat(validRefreshToken.isRevoked()).isTrue();
            verify(refreshTokenService, times(1)).saveToken(validRefreshToken);
        }
    }

}
