package com.minh.paymentsystem.auth.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refresh_token";

    private final boolean secure;

    public RefreshTokenCookieProvider(@Value("${auth.refresh-token-cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    public String createCookie(String refreshToken, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build()
                .toString();
    }

    public String clearCookie() {
        return createCookie("", Duration.ZERO);
    }
}
