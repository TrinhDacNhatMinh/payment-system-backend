package com.minh.paymentsystem.transfer.security;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdempotencyKey {

    public static final String HEADER_NAME = "Idempotency-Key";

    private static final Pattern UUID_V4 = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}");

    public static UUID parse(List<String> values) {
        if (values == null || values.size() != 1 || values.get(0) == null || !UUID_V4.matcher(values.get(0)).matches()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        }
        return UUID.fromString(values.get(0));
    }
}
