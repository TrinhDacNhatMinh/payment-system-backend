package com.minh.paymentsystem.auth.security.cors;

import java.io.IOException;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;

import com.minh.paymentsystem.auth.security.AuthAccessDeniedHandler;
import com.minh.paymentsystem.common.exception.ErrorCode;

@RequiredArgsConstructor
public class AuthCorsProcessor extends DefaultCorsProcessor {

    private final AuthAccessDeniedHandler accessDeniedHandler;

    @Override
    public boolean processRequest(@Nullable CorsConfiguration configuration, @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response) throws IOException {
        try {
            // DefaultCorsProcessor rejects malformed origins before handleInternal can classify them.
            CorsUtils.isCorsRequest(request);
        } catch (IllegalArgumentException exception) {
            accessDeniedHandler.writeError(response, ErrorCode.ORIGIN_NOT_ALLOWED);
            return false;
        }
        return super.processRequest(configuration, request, response);
    }

    @Override
    protected boolean handleInternal(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
            @NonNull CorsConfiguration configuration, boolean preFlightRequest) throws IOException {
        List<String> allowedOrigins = configuration.getAllowedOrigins();
        if (allowedOrigins == null || !allowedOrigins.contains(request.getHeaders().getOrigin())) {
            writeError(response, ErrorCode.ORIGIN_NOT_ALLOWED);
            return false;
        }
        return super.handleInternal(request, response, configuration, preFlightRequest);
    }

    @Override
    protected void rejectRequest(@NonNull ServerHttpResponse response) throws IOException {
        writeError(response, ErrorCode.ACCESS_DENIED);
    }

    private void writeError(ServerHttpResponse response, ErrorCode errorCode) throws IOException {
        if (response instanceof ServletServerHttpResponse servletResponse) {
            accessDeniedHandler.writeError(servletResponse.getServletResponse(), errorCode);
            return;
        }
        super.rejectRequest(response);
    }
}
