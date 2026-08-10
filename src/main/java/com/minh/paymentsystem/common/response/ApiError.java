package com.minh.paymentsystem.common.response;

public record ApiError(
        String code,
        String message
) {}
