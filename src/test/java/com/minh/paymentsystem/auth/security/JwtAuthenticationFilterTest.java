package com.minh.paymentsystem.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private FilterChain filterChain;
    @Mock
    private HandlerExceptionResolver exceptionResolver;
    @InjectMocks
    private JwtAuthenticationFilter filter;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateValidTokenAndContinueChain() throws Exception {
        request.addHeader("Authorization", "Bearer valid-token");
        UserDetails user = User.withUsername("user@example.com")
                .password("encoded")
                .roles("USER")
                .build();
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(user);
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
        assertThat(request.getAttribute("jwt_error")).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenTokenIsMissing() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenProvider, userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldKeepExpiredTokenErrorAndContinueChain() throws Exception {
        request.addHeader("Authorization", "Bearer expired-token");
        when(tokenProvider.getEmailFromToken("expired-token")).thenThrow(new ExpiredJwtException(null, null, "Expired"));

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute("jwt_error")).isEqualTo(ErrorCode.TOKEN_EXPIRED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldKeepInvalidTokenErrorAndContinueChain() throws Exception {
        request.addHeader("Authorization", "Bearer invalid-token");
        when(tokenProvider.getEmailFromToken("invalid-token")).thenThrow(new MalformedJwtException("Invalid"));

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute("jwt_error")).isEqualTo(ErrorCode.TOKEN_INVALID);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldDelegateUserLookupFailureAndStopChain() throws Exception {
        request.addHeader("Authorization", "Bearer valid-token");
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        IllegalStateException failure = new IllegalStateException("Unavailable");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenThrow(failure);
        when(exceptionResolver.resolveException(request, response, null, failure)).thenReturn(new ModelAndView());

        filter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute("jwt_error")).isNull();
        verify(exceptionResolver).resolveException(request, response, null, failure);
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldRejectLockedUserBeforeAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer valid-token");
        UserDetails user = User.withUsername("user@example.com")
                .password("encoded")
                .roles("USER")
                .accountLocked(true)
                .build();
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(user);
        when(exceptionResolver.resolveException(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.eq(response), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ModelAndView());

        filter.doFilter(request, response, filterChain);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionResolver).resolveException(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.eq(response), org.mockito.ArgumentMatchers.isNull(), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) captor.getValue()).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldMarkDeletedUserAsInvalidToken() throws Exception {
        request.addHeader("Authorization", "Bearer valid-token");
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("user@example.com");
        when(userDetailsService.loadUserByUsername("user@example.com")).thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute("jwt_error")).isEqualTo(ErrorCode.TOKEN_INVALID);
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(exceptionResolver);
    }

    @Test
    void shouldPropagateFailureWhenNoExceptionResolverHandlesIt() {
        request.addHeader("Authorization", "Bearer valid-token");
        IllegalStateException failure = new IllegalStateException("Unavailable");
        when(tokenProvider.getEmailFromToken("valid-token")).thenThrow(failure);

        assertThat(assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, filterChain))).isSameAs(failure);
        verifyNoInteractions(filterChain);
    }
}
