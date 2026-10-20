package com.minh.paymentsystem.auth.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minh.paymentsystem.auth.dto.AuthResponse;
import com.minh.paymentsystem.auth.dto.CsrfTokenResponse;
import com.minh.paymentsystem.auth.dto.LoginRequest;
import com.minh.paymentsystem.auth.dto.RegisterRequest;
import com.minh.paymentsystem.auth.security.cookie.RefreshTokenCookieProvider;
import com.minh.paymentsystem.auth.service.AuthService;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.user.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Authentication and Authorization API endpoints")
@SecurityRequirements
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Get CSRF token", description = "Sets an HttpOnly XSRF-TOKEN session cookie and returns its masked token. Send the returned token in X-XSRF-TOKEN and an allowed Origin on authentication POST requests. Obtain a new token after successful login or logout. No bearer token is required.")
    @ApiResponse(responseCode = "200", description = "CSRF token issued", headers = {
            @Header(name = HttpHeaders.SET_COOKIE, description = "HttpOnly XSRF-TOKEN cookie; Path=/api/v1/auth; SameSite=Lax", schema = @Schema(type = "string"))
    })
    @GetMapping("/csrf")
    public ResponseEntity<BaseResponse<CsrfTokenResponse>> csrf(@Parameter(hidden = true) CsrfToken csrfToken) {
        CsrfTokenResponse response = CsrfTokenResponse.from(csrfToken);
        return ResponseEntity
                .ok()
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Register new user", description = "Creates a new user account with role USER.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User successfully registered"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "403", description = "CSRF_TOKEN_MISSING, CSRF_TOKEN_INVALID, or ORIGIN_NOT_ALLOWED"),
            @ApiResponse(responseCode = "409", description = "Email already exists")
    })
    @Parameters({
            @Parameter(name = "Origin", in = ParameterIn.HEADER, required = true, description = "Exact frontend origin configured in auth.allowed-origins", example = "http://localhost:5173"),
            @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Masked token returned by GET /api/v1/auth/csrf; send its matching XSRF-TOKEN cookie")
    })
    @PostMapping("/register")
    public ResponseEntity<BaseResponse<UserResponse>> register(@RequestBody @Valid RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Login", description = "Authenticates the user and revokes all existing refresh tokens. Returns accessToken, tokenType, expiresIn in seconds, and user { id, email, role } in JSON, and sets the refresh token in an HttpOnly cookie. Existing access tokens remain valid until expiration.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated", headers = {
                    @Header(name = HttpHeaders.SET_COOKIE, description = "Sets HttpOnly refresh_token with Path=/api/v1/auth and SameSite=Lax; also expires the old XSRF-TOKEN cookie", schema = @Schema(type = "string"))
            }),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "403", description = "Account locked, CSRF_TOKEN_MISSING, CSRF_TOKEN_INVALID, or ORIGIN_NOT_ALLOWED")
    })
    @Parameters({
            @Parameter(name = "Origin", in = ParameterIn.HEADER, required = true, description = "Exact frontend origin configured in auth.allowed-origins", example = "http://localhost:5173"),
            @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Masked token returned by GET /api/v1/auth/csrf; send its matching XSRF-TOKEN cookie")
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

    @Operation(summary = "Refresh token", description = "Reads the refresh_token cookie; no request body is required. Revokes all existing refresh tokens, returns accessToken, tokenType, expiresIn in seconds, and the current user { id, email, role } in JSON, and replaces the HttpOnly refresh cookie. Locked accounts cannot refresh tokens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Token refreshed successfully", headers = {
                    @Header(name = HttpHeaders.SET_COOKIE, description = "Replaces the HttpOnly refresh_token cookie", schema = @Schema(type = "string"))
            }),
            @ApiResponse(responseCode = "401", description = "Refresh token cookie missing, empty, expired, or revoked"),
            @ApiResponse(responseCode = "403", description = "Account locked, CSRF_TOKEN_MISSING, CSRF_TOKEN_INVALID, or ORIGIN_NOT_ALLOWED"),
            @ApiResponse(responseCode = "404", description = "Refresh token not found")
    })
    @Parameters({
            @Parameter(name = "Origin", in = ParameterIn.HEADER, required = true, description = "Exact frontend origin configured in auth.allowed-origins", example = "http://localhost:5173"),
            @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Masked token returned by GET /api/v1/auth/csrf; send its matching XSRF-TOKEN cookie")
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
                    @Header(name = HttpHeaders.SET_COOKIE, description = "Clears refresh_token and XSRF-TOKEN using Max-Age=0 and their original cookie scope", schema = @Schema(type = "string"))
            }),
            @ApiResponse(responseCode = "401", description = "Refresh token cookie missing, empty, or revoked"),
            @ApiResponse(responseCode = "403", description = "CSRF_TOKEN_MISSING, CSRF_TOKEN_INVALID, or ORIGIN_NOT_ALLOWED"),
            @ApiResponse(responseCode = "404", description = "Refresh token not found")
    })
    @Parameters({
            @Parameter(name = "Origin", in = ParameterIn.HEADER, required = true, description = "Exact frontend origin configured in auth.allowed-origins", example = "http://localhost:5173"),
            @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true, description = "Masked token returned by GET /api/v1/auth/csrf; send its matching XSRF-TOKEN cookie")
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
