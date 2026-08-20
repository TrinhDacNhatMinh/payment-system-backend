package com.minh.paymentsystem.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid input data"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Account is locked"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "JWT token has expired"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Invalid JWT token"),
    REFRESH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "Refresh token not found"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token has expired"),
    REFRESH_TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "Wallet not found"),
    WALLET_INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Insufficient balance"),
    SELF_TRANSFER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Cannot transfer money to yourself"),
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Recipient not found"),
    RECIPIENT_LOCKED(HttpStatus.BAD_REQUEST, "Recipient account is locked"),
    AMOUNT_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "Amount is out of allowed range"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification detected on wallet"),
    PAYMENT_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment order not found"),
    INVALID_CHECKSUM(HttpStatus.BAD_REQUEST, "Invalid checksum"),
    DUPLICATE_ORDER(HttpStatus.OK, "Duplicate order"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus httpStatus;
    private final String message;
}
