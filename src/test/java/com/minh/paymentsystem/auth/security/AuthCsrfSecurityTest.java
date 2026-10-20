package com.minh.paymentsystem.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import jakarta.servlet.http.Cookie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.method.annotation.CsrfTokenArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.minh.paymentsystem.auth.controller.AuthController;
import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.service.AuthService;
import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.exception.GlobalExceptionHandler;
import com.minh.paymentsystem.config.SecurityConfig;

class AuthCsrfSecurityTest {

    private static final String ORIGIN = "http://localhost:5173";
    private static final String AUTH_PATH = "/api/v1/auth";
    private static final String REFRESH_COOKIE = "refresh_token=new-refresh; Path=/api/v1/auth; HttpOnly; SameSite=Lax";

    private final AuthService authService = mock(AuthService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private MockMvc mvc;
    @BeforeEach
    void setUp() {
        AuthAccessDeniedHandler deniedHandler = new AuthAccessDeniedHandler(objectMapper);
        CookieCsrfTokenRepository repository = SecurityConfig.csrfTokenRepository(false);
        CsrfFilter csrfFilter = new CsrfFilter(repository);
        csrfFilter.setRequireCsrfProtectionMatcher(AuthRequestFilter.PROTECTED_REQUESTS);
        csrfFilter.setRequestHandler(new XorCsrfTokenRequestAttributeHandler());
        csrfFilter.setAccessDeniedHandler(deniedHandler);
        mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler(), new AuthCsrfCookieAdvice(repository))
                .setCustomArgumentResolvers(new CsrfTokenArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(new AuthRequestFilter(Set.of(ORIGIN), deniedHandler), csrfFilter)
                .build();
    }

    @Test
    void shouldReturnMaskedTokenAndHttpOnlySessionCookieWithoutCreatingSession() throws Exception {
        MvcResult result = mvc.perform(get(AUTH_PATH + "/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.getPath()).isEqualTo(AUTH_PATH);
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getMaxAge()).isEqualTo(-1);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("SameSite=Lax");
        assertThat(readToken(result)).isNotBlank().isNotEqualTo(cookie.getValue());
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(result.getRequest().getSession(false)).isNull();
        verifyNoInteractions(authService);
    }

    @Test
    void shouldReuseRepositoryCookieOnRepeatedGet() throws Exception {
        CsrfSession session = getCsrfSession();

        MvcResult result = mvc.perform(get(AUTH_PATH + "/csrf").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNull();
        assertThat(readToken(result)).isNotEqualTo(session.token());
        when(authService.refresh("session-refresh")).thenReturn(authResponse());
        mvc.perform(authPost("refresh").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, readToken(result)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "register",
            "login",
            "refresh",
            "logout"
    })
    void shouldRejectMissingHeaderBeforeService(String endpoint) throws Exception {
        CsrfSession session = getCsrfSession();

        mvc.perform(authPost(endpoint).cookie(session.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_MISSING"));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "register",
            "login",
            "refresh",
            "logout"
    })
    void shouldRejectInvalidTokenBeforeService(String endpoint) throws Exception {
        CsrfSession session = getCsrfSession();

        mvc.perform(authPost(endpoint).cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, "invalid-token"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));

        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {
            "null",
            "https://localhost:5173",
            "http://localhost:5173/",
            "http://localhost:5173.evil.test"
    })
    void shouldRejectUntrustedOriginBeforeService(String origin) throws Exception {
        CsrfSession session = getCsrfSession();
        MockHttpServletRequestBuilder request = post(AUTH_PATH + "/refresh")
                .servletPath(AUTH_PATH + "/refresh")
                .cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.token());
        request.header(HttpHeaders.ORIGIN, origin);

        mvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.error.code").value("ORIGIN_NOT_ALLOWED"));

        verifyNoInteractions(authService);
    }

    @Test
    void shouldRejectRawCookieTokenAndQueryParameterFallback() throws Exception {
        CsrfSession session = getCsrfSession();
        mvc.perform(authPost("refresh").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.cookie().getValue()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
        mvc.perform(authPost("refresh").cookie(session.cookie()).param("_csrf", session.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_MISSING"));
        verifyNoInteractions(authService);
    }

    @Test
    void shouldRejectMissingOriginAndMultipleOrigins() throws Exception {
        mvc.perform(post(AUTH_PATH + "/login").servletPath(AUTH_PATH + "/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORIGIN_NOT_ALLOWED"));
        mvc.perform(authPost("login").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORIGIN_NOT_ALLOWED"));
        verifyNoInteractions(authService);
    }

    @Test
    void shouldRejectTokenFromAnotherCookie() throws Exception {
        CsrfSession first = getCsrfSession();
        CsrfSession second = getCsrfSession();
        mvc.perform(authPost("refresh").cookie(second.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, first.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_INVALID"));
        verifyNoInteractions(authService);
    }

    @Test
    void shouldRejectBlankHeader() throws Exception {
        CsrfSession session = getCsrfSession();
        mvc.perform(authPost("refresh").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, " "))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_MISSING"));
        verifyNoInteractions(authService);
    }

    @Test
    void shouldRejectHeaderWithoutRepositoryCookie() throws Exception {
        CsrfSession session = getCsrfSession();
        mvc.perform(authPost("refresh").header(AuthRequestFilter.CSRF_HEADER_NAME, session.token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CSRF_TOKEN_MISSING"));
        verifyNoInteractions(authService);
    }

    @Test
    void shouldAllowRegistrationWithValidOriginAndCsrf() throws Exception {
        CsrfSession session = getCsrfSession();
        mvc.perform(authPost("register").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.token())
                .content("{\"email\":\"user@example.com\",\"password\":\"Password123!\",\"fullName\":\"Test User\",\"phone\":\"0901234567\"}"))
                .andExpect(status().isCreated());
        verify(authService).register(any());
    }

    @Test
    void shouldClearCsrfCookieAfterLoginWithoutOverwritingRefreshCookie() throws Exception {
        CsrfSession session = getCsrfSession();
        when(authService.login(any())).thenReturn(authResponse());

        MvcResult result = mvc.perform(authPost("login").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.token())
                .content("{\"email\":\"user@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertClearedCsrfCookie(result);
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).contains(REFRESH_COOKIE);
        verify(authService).login(any());
    }

    @Test
    void shouldKeepCsrfCookieAfterRefresh() throws Exception {
        CsrfSession session = getCsrfSession();
        when(authService.refresh("session-refresh")).thenReturn(authResponse());

        MvcResult result = mvc.perform(authPost("refresh").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.token()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNull();
        verify(authService).refresh("session-refresh");
    }

    @Test
    void shouldClearCsrfCookieAfterLogout() throws Exception {
        CsrfSession session = getCsrfSession();
        when(authService.logout("session-refresh")).thenReturn("refresh_token=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax");

        MvcResult result = mvc.perform(authPost("logout").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.token()))
                .andExpect(status().isOk())
                .andReturn();

        assertClearedCsrfCookie(result);
        assertThat(result.getResponse().getCookie("refresh_token").getMaxAge()).isZero();
        verify(authService).logout("session-refresh");
    }

    @Test
    void shouldPreserveCookiesOnFailedLogin() throws Exception {
        CsrfSession session = getCsrfSession();
        when(authService.login(any())).thenThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        MvcResult result = mvc.perform(authPost("login").cookie(session.cookie())
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.token())
                .content("{\"email\":\"user@example.com\",\"password\":\"Password123!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void shouldKeepMissingRefreshCookieErrorAfterValidCsrf() throws Exception {
        CsrfSession session = getCsrfSession();
        when(authService.refresh(null)).thenThrow(new BusinessException(ErrorCode.REFRESH_TOKEN_MISSING));

        mvc.perform(post(AUTH_PATH + "/refresh")
                .servletPath(AUTH_PATH + "/refresh")
                .cookie(session.cookie())
                .header(HttpHeaders.ORIGIN, ORIGIN)
                .header(AuthRequestFilter.CSRF_HEADER_NAME, session.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.error.code").value("REFRESH_TOKEN_MISSING"));

        verify(authService).refresh(null);
    }

    @Test
    void shouldSetSecureFlagForHttpsConfiguration() {
        CookieCsrfTokenRepository secureRepository = SecurityConfig.csrfTokenRepository(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        secureRepository.saveToken(secureRepository.generateToken(request), request, response);

        assertThat(response.getCookie("XSRF-TOKEN").getSecure()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/transfer",
            "/api/v1/payment/vnpay-webhook",
            "/api/v1/payment/vnpay-return"
    })
    void shouldExcludeBearerApisAndVnpayFromCsrfMatcher(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        assertThat(AuthRequestFilter.PROTECTED_REQUESTS.matches(request)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "GET",
            "HEAD",
            "OPTIONS"
    })
    void shouldExcludeSafeMethodsFromCsrfMatcher(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, AUTH_PATH + "/login");
        request.setServletPath(AUTH_PATH + "/login");
        assertThat(AuthRequestFilter.PROTECTED_REQUESTS.matches(request)).isFalse();
    }

    private CsrfSession getCsrfSession() throws Exception {
        MvcResult result = mvc.perform(get(AUTH_PATH + "/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new CsrfSession(result.getResponse().getCookie("XSRF-TOKEN"), readToken(result));
    }

    private String readToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("token")
                .asText();
    }

    private MockHttpServletRequestBuilder authPost(String endpoint) {
        String path = AUTH_PATH + "/" + endpoint;
        return post(path)
                .servletPath(path)
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(new Cookie(RefreshTokenCookieProvider.COOKIE_NAME, "session-refresh"))
                .header(HttpHeaders.ORIGIN, ORIGIN);
    }

    private AuthResponse authResponse() {
        return new AuthResponse("access-token", "Bearer", 1800, null, REFRESH_COOKIE);
    }

    private void assertClearedCsrfCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getPath()).isEqualTo(AUTH_PATH);
        assertThat(cookie.isHttpOnly()).isTrue();
    }

    private record CsrfSession(
            Cookie cookie,
            String token) {
    }
}
