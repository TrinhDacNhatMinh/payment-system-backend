package com.minh.paymentsystem.common.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minh.paymentsystem.auth.security.CustomUserDetails;
import com.minh.paymentsystem.auth.security.JwtAuthenticationEntryPoint;
import com.minh.paymentsystem.auth.security.JwtAuthenticationFilter;
import com.minh.paymentsystem.auth.security.JwtTokenProvider;
import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.config.SecurityConfig;
import com.minh.paymentsystem.transaction.controller.AdminTransactionController;
import com.minh.paymentsystem.transaction.controller.TransactionController;
import com.minh.paymentsystem.transaction.dto.TransactionFilterRequest;
import com.minh.paymentsystem.transaction.service.TransactionService;
import com.minh.paymentsystem.user.controller.AdminUserController;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.service.UserService;

@WebMvcTest({
        AdminUserController.class,
        AdminTransactionController.class,
        TransactionController.class
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class
})
class ApiBoundaryTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private TransactionService transactionService;
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @ParameterizedTest
    @CsvSource({
            "/api/v1/admin/users,page,-1",
            "/api/v1/admin/users,size,0",
            "/api/v1/admin/users,size,-1",
            "/api/v1/admin/users,size,101",
            "/api/v1/admin/users,size,abc",
            "/api/v1/admin/transactions,page,-1",
            "/api/v1/admin/transactions,size,0",
            "/api/v1/admin/transactions,size,101",
            "/api/v1/admin/transactions,page,abc"
    })
    @WithMockUser(roles = "ADMIN")
    void shouldRejectInvalidAdminPagination(String path, String parameter, String value) throws Exception {
        mvc.perform(get(path).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(userService, transactionService);
    }

    @ParameterizedTest
    @CsvSource({
            "page,-1",
            "size,0",
            "size,-1",
            "size,101",
            "size,abc"
    })
    void shouldRejectInvalidPersonalPagination(String parameter, String value) throws Exception {
        mvc.perform(get("/api/v1/transactions").with(user(activeUser())).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(transactionService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowMaximumAdminUserPageSize() throws Exception {
        when(userService.getAllUsers(any(Pageable.class))).thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mvc.perform(get("/api/v1/admin/users").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowMaximumAdminTransactionPageSize() throws Exception {
        when(transactionService.getAllTransactions(any(), any(Pageable.class))).thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mvc.perform(get("/api/v1/admin/transactions").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void shouldKeepDefaultPersonalPagination() throws Exception {
        when(transactionService.getMyTransactions(eq(1L), any())).thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mvc.perform(get("/api/v1/transactions").with(user(activeUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(10));

        verify(transactionService).getMyTransactions(1L, new TransactionFilterRequest(null, null, null, null, 0, 10));
    }

    @Test
    void shouldAllowMaximumPersonalPageSize() throws Exception {
        when(transactionService.getMyTransactions(eq(1L), any())).thenReturn(new PageResponse<>(List.of(), 0, 100, 0, 0));

        mvc.perform(get("/api/v1/transactions").with(user(activeUser())).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void shouldAuthenticateValidJwt() throws Exception {
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(activeUser());
        when(transactionService.getMyTransactions(eq(1L), any())).thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0));

        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldRejectLockedJwtUserWithForbiddenResponse() throws Exception {
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com"))
                .thenReturn(org.springframework.security.core.userdetails.User.withUsername("user@example.com")
                        .password("encoded")
                        .roles("USER")
                        .accountLocked(true)
                        .build());

        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));

        verifyNoInteractions(transactionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/transactions",
            "/api/v1/payment/vnpay-return"
    })
    void shouldReturnServerErrorForDatabaseFailureOnPublicOrProtectedPath(String path) throws Exception {
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com"))
                .thenThrow(new DataAccessResourceFailureException("Database details must not reach the client"));

        mvc.perform(get(path).header("Authorization", "Bearer valid-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldNotClassifyUserLoadingArgumentFailureAsInvalidJwt() throws Exception {
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenThrow(new IllegalArgumentException("Invalid stored user"));

        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void shouldRejectJwtForDeletedUserWithUnauthorizedResponse() throws Exception {
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void shouldRejectMalformedJwtWithUnauthorizedResponse() throws Exception {
        when(tokenProvider.getEmailFromToken("invalid-token")).thenThrow(new MalformedJwtException("Malformed"));

        mvc.perform(get("/api/v1/transactions").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    private CustomUserDetails activeUser() {
        return new CustomUserDetails(User.builder()
                .id(1L)
                .email("user@example.com")
                .password("encoded")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }
}
