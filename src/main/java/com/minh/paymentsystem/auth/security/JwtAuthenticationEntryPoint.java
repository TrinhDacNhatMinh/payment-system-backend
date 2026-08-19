package com.minh.paymentsystem.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.common.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException, ServletException {

        ErrorCode errorCode = (ErrorCode) request.getAttribute("jwt_error");

        if (errorCode == null) {
            // Unauthenticated without a specific JWT error
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiResponse<Void> apiResponse = ApiResponse.error(
                    "UNAUTHORIZED",
                    "Full authentication is required to access this resource");
            objectMapper.writeValue(response.getOutputStream(), apiResponse);
            return;
        }

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> apiResponse = ApiResponse.error(errorCode.name(), errorCode.getMessage());
        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
