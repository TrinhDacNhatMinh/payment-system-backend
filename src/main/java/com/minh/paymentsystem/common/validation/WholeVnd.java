package com.minh.paymentsystem.common.validation;

import java.math.BigDecimal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WholeVnd {

    public static boolean isWhole(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() <= 0;
    }

    public static void requirePositiveAmount(BigDecimal value) {
        if (!isWhole(value) || value.signum() <= 0 || value.precision() - value.scale() > 16) {
            throw new BusinessException(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }
    }

    public static void requireWritableBalance(BigDecimal value) {
        if (!isWhole(value)) {
            throw new BusinessException(ErrorCode.LEGACY_FRACTIONAL_MONEY);
        }
        if (value.signum() < 0 || value.precision() - value.scale() > 16) {
            throw new BusinessException(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }
    }
}
