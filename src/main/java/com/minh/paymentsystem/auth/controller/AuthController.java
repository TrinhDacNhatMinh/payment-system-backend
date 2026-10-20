package com.minh.paymentsystem.auth.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.auth.security.RefreshTokenCookieProvider;
import com.minh.paymentsystem.auth.service.AuthService;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.user.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication and Authorization API endpoints")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register new user", description = "Creates a new user account with role USER.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User successfully registered"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<BaseResponse<UserResponse>> register(@RequestBody @Valid RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Login", description = "Authenticates the user and revokes all existing refresh tokens. Returns the access token in JSON and sets the refresh token in an HttpOnly cookie. Existing access tokens remain valid until expiration.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated", headers = {
                    @Header(name = HttpHeaders.SET_COOKIE, description = "HttpOnly refresh_token cookie with Path=/api/v1/auth and SameSite=Lax", schema = @Schema(type = "string"))
            }),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "403", description = "Account is locked")
    })
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@RequestBody @Valid LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, response.cookie())
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Refresh token", description = "Reads the refresh_token cookie; no request body is required. Revokes all existing refresh tokens, returns a new access token in JSON, and replaces the HttpOnly refresh cookie. Locked accounts cannot refresh tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully", headers = {
                    @Header(name = HttpHeaders.SET_COOKIE, description = "Replaces the HttpOnly refresh_token cookie", schema = @Schema(type = "string"))
            }),
            @ApiResponse(responseCode = "401", description = "Refresh token cookie missing, empty, expired, or revoked"),
            @ApiResponse(responseCode = "403", description = "Account is locked"),
            @ApiResponse(responseCode = "404", description = "Refresh token not found")
    })
    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refresh(
            @Parameter(required = true, description = "HttpOnly refresh token cookie issued by login or the previous refresh") @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken) {
        AuthResponse response = authService.refresh(refreshToken);
        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, response.cookie())
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Logout", description = "Reads the refresh_token cookie; no request body is required. Revokes all refresh tokens for the user and clears the cookie on success. The token may be expired but must exist and must not be revoked. Existing access tokens remain valid until expiration.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully logged out", headers = {
                    @Header(name = HttpHeaders.SET_COOKIE, description = "Clears the refresh_token cookie using Max-Age=0 and the original cookie scope", schema = @Schema(type = "string"))
            }),
            @ApiResponse(responseCode = "401", description = "Refresh token cookie missing, empty, or revoked"),
            @ApiResponse(responseCode = "404", description = "Refresh token not found")
    })
    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(
            @Parameter(required = true, description = "HttpOnly refresh token cookie identifying the session to revoke") @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken) {
        String cookie = authService.logout(refreshToken);
        return ResponseEntity
                .status(HttpStatus.OK)
                .header(HttpHeaders.SET_COOKIE, cookie)
                .body(BaseResponse.success(null));
    }
}
