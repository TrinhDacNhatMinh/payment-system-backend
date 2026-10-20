package com.minh.paymentsystem.auth.service;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.user.dto.UserResponse;

public interface AuthService {
    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(String refreshToken);

    /**
     * Revokes the user's refresh tokens and returns the Set-Cookie header value that clears the refresh cookie.
     */
    String logout(String refreshToken);
}
