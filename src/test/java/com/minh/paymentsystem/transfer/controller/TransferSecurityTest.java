package com.minh.paymentsystem.transfer.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minh.paymentsystem.auth.security.AuthAccessDeniedHandler;
import com.minh.paymentsystem.auth.security.jwt.JwtAuthenticationEntryPoint;
import com.minh.paymentsystem.auth.security.jwt.JwtAuthenticationFilter;
import com.minh.paymentsystem.auth.security.jwt.JwtTokenProvider;
import com.minh.paymentsystem.auth.security.user.CustomUserDetails;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.exception.GlobalExceptionHandler;
import com.minh.paymentsystem.config.SecurityConfig;
import com.minh.paymentsystem.transfer.dto.TransferRequestResponse;
import com.minh.paymentsystem.transfer.dto.TransferRequestSnapshot;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.transfer.service.TransferService;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;

@WebMvcTest(controllers = TransferController.class, properties = {
        "auth.allowed-origins=http://localhost:5173",
        "auth.refresh-token-cookie.secure=false"
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        AuthAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class TransferSecurityTest {

    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private TransferService transferService;
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void shouldAllowIdempotencyPreflightWithoutAuthentication() throws Exception {
        mvc.perform(options("/api/v1/transfer")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
        verifyNoInteractions(transferService);
    }

    @Test
    void shouldRequireAuthenticationAndDisableCachingOnBothEndpoints() throws Exception {
        mvc.perform(post("/api/v1/transfer").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(get("/api/v1/transfer/requests/" + KEY).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(transferService);
    }

    @Test
    void shouldRejectNumericMoneyBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/transfer").with(authentication(principal()))
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toEmail\":\"recipient@example.com\",\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(transferService);
    }

    @Test
    void shouldUsePrincipalOwnershipForLookupAndReturnNullOutcomeForPending() throws Exception {
        when(transferService.getRequest(2L, KEY)).thenReturn(new TransferRequestResponse(
                java.util.UUID.fromString(KEY), TransferRequestState.PENDING,
                new TransferRequestSnapshot("recipient@example.com", new java.math.BigDecimal("1000"), null), null, null, false));

        mvc.perform(get("/api/v1/transfer/requests/" + KEY).with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("PENDING"))
                .andExpect(jsonPath("$.data.request.amount").value("1000"))
                .andExpect(jsonPath("$.data.result").isEmpty())
                .andExpect(jsonPath("$.data.error").isEmpty())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verify(transferService).getRequest(2L, KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"999", "50000001", "9999999999999999"})
    void shouldRejectAmountsOutsideTransferLimitsBeforeCallingService(String amount) throws Exception {
        mvc.perform(post("/api/v1/transfer").with(authentication(principal()))
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"toEmail\":\"recipient@example.com\",\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(transferService);
    }

    @Test
    void shouldKeepCorsAndNoStoreHeadersOnKeyErrors() throws Exception {
        when(transferService.transfer(eq(2L), eq(List.of(KEY)), any()))
                .thenThrow(new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED));

        mvc.perform(post("/api/v1/transfer").with(authentication(principal()))
                .header(HttpHeaders.ORIGIN, ORIGIN).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toEmail\":\"recipient@example.com\",\"amount\":\"1000\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void shouldRejectLockedAccountBeforeReplay() throws Exception {
        User locked = User.builder().id(2L).email("locked@example.com").role(Role.USER).status(UserStatus.LOCKED).build();
        when(tokenProvider.getEmailFromToken("locked-token")).thenReturn(locked.getEmail());
        when(userDetailsService.loadUserByUsername(locked.getEmail())).thenReturn(new CustomUserDetails(locked));

        mvc.perform(get("/api/v1/transfer/requests/" + KEY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer locked-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_LOCKED"));
        verifyNoInteractions(transferService);
    }

    private UsernamePasswordAuthenticationToken principal() {
        CustomUserDetails user = new CustomUserDetails(User.builder().id(2L).email("sender@example.com")
                .role(Role.USER).status(UserStatus.ACTIVE).build());
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
