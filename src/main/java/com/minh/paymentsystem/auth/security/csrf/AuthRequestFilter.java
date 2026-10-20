package com.minh.paymentsystem.auth.security.csrf;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.minh.paymentsystem.auth.security.AuthAccessDeniedHandler;
import com.minh.paymentsystem.common.exception.ErrorCode;

@RequiredArgsConstructor
public class AuthRequestFilter extends OncePerRequestFilter {

    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    public static final RequestMatcher PROTECTED_REQUESTS = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/v1/auth/register", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/login", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/refresh", "POST"),
            new AntPathRequestMatcher("/api/v1/auth/logout", "POST"));

    private final Set<String> allowedOrigins;
    private final AuthAccessDeniedHandler accessDeniedHandler;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !PROTECTED_REQUESTS.matches(request);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!isAllowedOrigin(request)) {
            accessDeniedHandler.writeError(response, ErrorCode.ORIGIN_NOT_ALLOWED);
            return;
        }
        if (!StringUtils.hasText(request.getHeader(CSRF_HEADER_NAME))) {
            accessDeniedHandler.writeError(response, ErrorCode.CSRF_TOKEN_MISSING);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(HttpServletRequest request) {
        List<String> origins = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
        return origins.size() == 1
                && StringUtils.hasText(origins.get(0))
                && !"null".equals(origins.get(0))
                && allowedOrigins.contains(origins.get(0));
    }
}
