package com.minh.paymentsystem.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.stereotype.Component;

import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.response.BaseResponse;

@Component
@Slf4j
@RequiredArgsConstructor
public class AuthAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
        if (exception instanceof MissingCsrfTokenException) {
            errorCode = ErrorCode.CSRF_TOKEN_MISSING;
        } else if (exception instanceof InvalidCsrfTokenException) {
            errorCode = ErrorCode.CSRF_TOKEN_INVALID;
        }
        writeError(response, errorCode);
    }

    public void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        log.warn("Request denied: code={}", errorCode);
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getOutputStream(), BaseResponse.error(errorCode.name(), errorCode.getMessage()));
    }
}
