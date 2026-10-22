package com.minh.paymentsystem.payment.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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
import com.minh.paymentsystem.payment.dto.DepositOrderResponse;
import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.enums.PaymentOrderStatus;
import com.minh.paymentsystem.payment.service.PaymentService;
import com.minh.paymentsystem.user.entity.Role;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;

@WebMvcTest(controllers = {
        PaymentController.class,
        PaymentWebhookController.class
}, properties = {
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
class PaymentSecurityTest {

    private static final String KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final String ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PaymentService service;
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void shouldAllowCredentialedPreflightWithIdempotencyKeyWithoutAuthentication() throws Exception {
        mvc.perform(options("/api/v1/payment/deposit")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/payment/orders/ORDER_1",
            "/api/v1/payment/deposit/requests/550e8400-e29b-41d4-a716-446655440000"
    })
    void shouldRequireAuthenticationForLookupAndDisableCaching(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRequireAuthenticationForDepositCreation() throws Exception {
        mvc.perform(post("/api/v1/payment/deposit").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"10000\"",
            "10000.50",
            "9999",
            "50000001",
            "null"
    })
    void shouldRejectInvalidMoneyBeforeCallingService(String amount) throws Exception {
        mvc.perform(post("/api/v1/payment/deposit").with(authentication(principal()))
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectUnknownBodyFieldsEvenWhenGlobalJacksonIgnoresThem() throws Exception {
        mvc.perform(post("/api/v1/payment/deposit").with(authentication(principal()))
                .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10000,\"userId\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldReturnCompleteDepositViewForTheAuthenticatedUser() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 12, 0);
        DepositOrderResponse response = new DepositOrderResponse("ORDER_1", UUID.fromString(KEY), new BigDecimal("10000"),
                "VND", PaymentOrderStatus.PENDING, now, now.plusMinutes(15), null, true,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?test=1");
        when(service.createDepositOrder(eq(2L), eq(List.of(KEY)), any(), anyString())).thenReturn(response);
        mvc.perform(post("/api/v1/payment/deposit").with(authentication(principal()))
                .header("Idempotency-Key", KEY).header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(10000))
                .andExpect(jsonPath("$.data.idempotencyKey").value(KEY))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.paymentUrlExpiresAt").value("2026-10-22T12:15:00"))
                .andExpect(jsonPath("$.data.paidAt").isEmpty())
                .andExpect(jsonPath("$.data.canContinuePayment").value(true))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void shouldUsePrincipalForBothLookupsAndKeepCorsHeadersOnErrors() throws Exception {
        when(service.getOrder(2L, "ORDER_1")).thenThrow(new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        when(service.getDepositRequest(2L, KEY)).thenThrow(new BusinessException(ErrorCode.DEPOSIT_REQUEST_NOT_FOUND));
        mvc.perform(get("/api/v1/payment/orders/ORDER_1").with(authentication(principal())).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_ORDER_NOT_FOUND"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(get("/api/v1/payment/deposit/requests/" + KEY).with(authentication(principal())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEPOSIT_REQUEST_NOT_FOUND"));
        verify(service).getOrder(2L, "ORDER_1");
        verify(service).getDepositRequest(2L, KEY);
    }

    @Test
    void shouldUse303RedirectForPublicReturnWithoutAResponseBody() throws Exception {
        when(service.processReturn(any())).thenReturn(URI.create("http://localhost:5173/payment/result?verification=invalid"));
        mvc.perform(get("/api/v1/payment/vnpay-return").param("vnp_TxnRef", "unverified"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:5173/payment/result?verification=invalid"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().string(""));
    }

    @Test
    void shouldKeepDuplicateQueryValuesForServiceToRejectAndUseVnpayEnvelope() throws Exception {
        when(service.handleCallback(any())).thenReturn(new VnpayCallbackResponse("99", "Invalid parameters"));
        mvc.perform(get("/api/v1/payment/vnpay-webhook").param("vnp_Amount", "1000000", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("99"))
                .andExpect(jsonPath("$.Message").value("Invalid parameters"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(service).handleCallback(argThat(params -> params.get("vnp_Amount").size() == 2));
    }

    private UsernamePasswordAuthenticationToken principal() {
        User user = User.builder().id(2L).email("owner@example.com").password("hash")
                .role(Role.USER).status(UserStatus.ACTIVE).build();
        CustomUserDetails details = new CustomUserDetails(user);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }
}
