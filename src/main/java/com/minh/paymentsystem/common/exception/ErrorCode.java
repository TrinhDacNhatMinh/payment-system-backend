package com.minh.paymentsystem.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid input data"),
    WALLET_INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Insufficient balance"),
    SELF_TRANSFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Cannot transfer money to yourself"),
    RECIPIENT_LOCKED(HttpStatus.BAD_REQUEST, "Recipient account is locked"),
    AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Amount is out of allowed range"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency key must be a single UUID v4"),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid JWT token"),
    REFRESH_TOKEN_MISSING(HttpStatus.UNAUTHORIZED, "Refresh token cookie is missing"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token has expired"),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "Refresh token not found"),

    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is locked"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    CSRF_TOKEN_MISSING(HttpStatus.FORBIDDEN, "CSRF token is missing"),
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "CSRF token is invalid"),
    ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "Request origin is not allowed"),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Wallet not found"),
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recipient not found"),
    PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment order not found"),
    DEPOSIT_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Deposit request not found"),
    TRANSFER_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Transfer request not found"),

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    DEPOSIT_KEY_REUSED(HttpStatus.CONFLICT, "Deposit key was already used with another amount"),
    DEPOSIT_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "Deposit request is being created; check its status"),
    AUTH_SESSION_BUSY(HttpStatus.CONFLICT, "Authentication session is busy; please try again"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification detected on wallet"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key was used with a different transfer payload"),
    TRANSFER_IN_PROGRESS(HttpStatus.CONFLICT, "Transfer request is being processed"),
    LEGACY_FRACTIONAL_MONEY(HttpStatus.CONFLICT, "Legacy fractional money requires reconciliation before further writes"),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus httpStatus;
    private final String message;
}
