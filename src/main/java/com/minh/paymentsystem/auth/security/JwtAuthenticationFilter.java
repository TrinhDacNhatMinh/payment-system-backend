package com.minh.paymentsystem.auth.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserDetailsService userDetailsService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractBearerToken(request);
            if (StringUtils.hasText(jwt)) {
                String email = resolveEmailFromJwt(jwt, request);
                if (email != null) {
                    authenticateRequest(email, request);
                }
            }
        } catch (BusinessException ex) {
            SecurityContextHolder.clearContext();
            if (ex.getErrorCode() != ErrorCode.USER_NOT_FOUND) {
                resolveFailure(request, response, ex);
                return;
            }
            request.setAttribute("jwt_error", ErrorCode.TOKEN_INVALID);
        } catch (UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
            request.setAttribute("jwt_error", ErrorCode.TOKEN_INVALID);
        } catch (RuntimeException ex) {
            // Servlet filters execute outside the controller exception-handling boundary.
            SecurityContextHolder.clearContext();
            resolveFailure(request, response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String resolveEmailFromJwt(String jwt, HttpServletRequest request) {
        try {
            String email = tokenProvider.getEmailFromToken(jwt);
            if (!StringUtils.hasText(email)) {
                throw new IllegalArgumentException("JWT subject is missing");
            }
            return email;
        } catch (ExpiredJwtException ex) {
            log.warn("JWT token expired: code={}", ErrorCode.TOKEN_EXPIRED);
            request.setAttribute("jwt_error", ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT token invalid: code={}", ErrorCode.TOKEN_INVALID);
            request.setAttribute("jwt_error", ErrorCode.TOKEN_INVALID);
        }
        SecurityContextHolder.clearContext();
        return null;
    }

    private void authenticateRequest(String email, HttpServletRequest request) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isAccountNonLocked()) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void resolveFailure(HttpServletRequest request, HttpServletResponse response, RuntimeException exception) {
        if (exceptionResolver.resolveException(request, response, null, exception) == null) {
            throw exception;
        }
    }
}
