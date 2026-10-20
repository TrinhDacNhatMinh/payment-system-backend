package com.minh.paymentsystem.auth.security.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CorsFilter;

import com.minh.paymentsystem.auth.controller.AuthController;
import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.AuthUserResponse;
import com.minh.paymentsystem.auth.security.AuthAccessDeniedHandler;
import com.minh.paymentsystem.auth.security.csrf.AuthCsrfCookieAdvice;
import com.minh.paymentsystem.auth.security.csrf.AuthRequestFilter;
import com.minh.paymentsystem.auth.security.jwt.JwtAuthenticationEntryPoint;
import com.minh.paymentsystem.auth.security.jwt.JwtAuthenticationFilter;
import com.minh.paymentsystem.auth.security.jwt.JwtTokenProvider;
import com.minh.paymentsystem.auth.service.AuthService;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.exception.GlobalExceptionHandler;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.config.SecurityConfig;
import com.minh.paymentsystem.user.entity.Role;

@WebMvcTest(controllers = AuthController.class, properties = {
        "auth.allowed-origins=http://localhost:5173",
        "auth.refresh-token-cookie.secure=false"
})
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        AuthAccessDeniedHandler.class,
        AuthCsrfCookieAdvice.class,
        GlobalExceptionHandler.class,
        CorsSecurityTest.ProbeController.class
})
class CorsSecurityTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static final String CSRF_PATH = "/api/v1/auth/csrf";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SecurityFilterChain securityFilterChain;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private JwtTokenProvider tokenProvider;
    @MockitoBean
    private UserDetailsService userDetailsService;

    @ParameterizedTest
    @ValueSource(strings = {
            "register",
            "login",
            "refresh",
            "logout"
    })
    void shouldAcceptAuthPreflightWithoutAuthenticationCookiesOrCsrf(String endpoint) throws Exception {
        MvcResult result = mvc.perform(options("/api/v1/auth/" + endpoint)
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .containsIgnoringCase("Content-Type")
                .containsIgnoringCase("Authorization")
                .containsIgnoringCase("X-XSRF-TOKEN");
        assertThat(result.getResponse().getHeaders(HttpHeaders.VARY))
                .contains(HttpHeaders.ORIGIN, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        verifyNoInteractions(authService, tokenProvider, userDetailsService);
    }

    @Test
    void shouldAllowAdminPatchPreflightWithoutGrantingAdminAccess() throws Exception {
        mvc.perform(options("/api/v1/admin/users/1/lock")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));

        mvc.perform(get("/api/v1/cors-test/admin").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void shouldReturnCsrfTokenWithCredentialedCorsHeaders() throws Exception {
        mvc.perform(get(CSRF_PATH).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .andExpect(jsonPath("$.data.headerName").value(AuthRequestFilter.CSRF_HEADER_NAME))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void shouldKeepCorsHeadersOnCsrfErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/login").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_MISSING"));
        verifyNoInteractions(authService);
    }

    @Test
    void shouldKeepCorsHeadersOnValidationErrors() throws Exception {
        MvcResult csrfResult = mvc.perform(get(CSRF_PATH).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();

        mvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(AuthRequestFilter.CSRF_HEADER_NAME, token)
                .cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(authService);
    }

    @Test
    void shouldKeepCorsHeadersAndCookiesUnchangedOnSessionLockFailure() throws Exception {
        MvcResult csrfResult = mvc.perform(get(CSRF_PATH).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
        when(authService.logout("session-refresh")).thenThrow(new BusinessException(ErrorCode.AUTH_SESSION_BUSY));

        mvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(AuthRequestFilter.CSRF_HEADER_NAME, token)
                .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"), new Cookie("refresh_token", "session-refresh")))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.error.code").value("AUTH_SESSION_BUSY"));
    }

    @Test
    void shouldReturnSessionAndCookiesForCredentialedLogin() throws Exception {
        MvcResult csrfResult = mvc.perform(get(CSRF_PATH).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
        when(authService.login(any())).thenReturn(new AuthResponse("access-token", "Bearer", 1800,
                new AuthUserResponse(1L, "user@example.com", Role.USER),
                "refresh_token=session-refresh; Path=/api/v1/auth; HttpOnly; SameSite=Lax"));

        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(AuthRequestFilter.CSRF_HEADER_NAME, token)
                .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getCookie("refresh_token").isHttpOnly()).isTrue();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();
        verify(authService).login(any());
    }

    @Test
    void shouldReturn401WithStableCodeWhenRefreshTokenIsNotFound() throws Exception {
        MvcResult csrfResult = mvc.perform(get(CSRF_PATH).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andReturn();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
        when(authService.refresh("unknown-refresh-token"))
                .thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        mvc.perform(post("/api/v1/auth/refresh")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(AuthRequestFilter.CSRF_HEADER_NAME, csrfToken)
                .cookie(csrfResult.getResponse().getCookie("XSRF-TOKEN"), new Cookie("refresh_token", "unknown-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_NOT_FOUND"));

        verify(authService).refresh("unknown-refresh-token");
        verifyNoMoreInteractions(authService);
    }

    @Test
    void shouldKeepCorsHeadersOnExpiredAccessTokenErrors() throws Exception {
        when(tokenProvider.getEmailFromToken("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "Expired"));

        mvc.perform(get("/api/v1/cors-test/admin")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.error.code").value("TOKEN_EXPIRED"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldKeepCorsHeadersOnAuthorizationErrors() throws Exception {
        mvc.perform(get("/api/v1/cors-test/admin").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }

    @Test
    @WithMockUser
    void shouldKeepCorsHeadersOnUnexpectedErrors() throws Exception {
        mvc.perform(get("/api/v1/cors-test/failure").header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.example",
            "null",
            "http://localhost:5173/",
            "http://localhost:5173.evil.example"
    })
    void shouldRejectUnlistedOriginsWithoutCorsAccessOrCookies(String origin) throws Exception {
        mvc.perform(get(CSRF_PATH).header(HttpHeaders.ORIGIN, origin))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.error.code").value("ORIGIN_NOT_ALLOWED"));
        verifyNoInteractions(authService, tokenProvider, userDetailsService);
    }

    @Test
    void shouldRejectPreflightFromUnlistedOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "https://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(jsonPath("$.error.code").value("ORIGIN_NOT_ALLOWED"));
        verifyNoInteractions(authService, tokenProvider, userDetailsService);
    }

    @Test
    void shouldRejectUnlistedPreflightHeaders() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-untrusted-header"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        verifyNoInteractions(authService, tokenProvider, userDetailsService);
    }

    @Test
    void shouldRejectUnlistedPreflightMethods() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        verifyNoInteractions(authService, tokenProvider, userDetailsService);
    }

    @Test
    void shouldAllowNonCorsRequestsWithoutAddingCorsHeaders() throws Exception {
        mvc.perform(get(CSRF_PATH))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void shouldProcessCorsBeforeCsrfOriginAndJwtFilters() {
        assertThat(securityFilterChain.getFilters())
                .filteredOn(CorsFilter.class::isInstance)
                .hasSize(1);
        assertThat(securityFilterChain.getFilters())
                .<Class<?>>extracting(Object::getClass)
                .containsSubsequence(CorsFilter.class, AuthRequestFilter.class, CsrfFilter.class, JwtAuthenticationFilter.class);
    }

    @RestController
    public static class ProbeController {

        @GetMapping("/api/v1/cors-test/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public BaseResponse<Void> admin() {
            return BaseResponse.success(null);
        }

        @GetMapping("/api/v1/cors-test/failure")
        public BaseResponse<Void> failure() {
            throw new IllegalStateException("Test failure");
        }
    }
}
