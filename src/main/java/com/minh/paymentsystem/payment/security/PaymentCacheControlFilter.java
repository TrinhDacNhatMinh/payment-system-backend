package com.minh.paymentsystem.payment.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class PaymentCacheControlFilter extends OncePerRequestFilter {

    private static final RequestMatcher PAYMENT_REQUESTS = new AntPathRequestMatcher("/api/v1/payment/**");

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !PAYMENT_REQUESTS.matches(request);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        filterChain.doFilter(request, response);
    }
}
