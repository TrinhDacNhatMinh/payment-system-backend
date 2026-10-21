package com.minh.paymentsystem.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;

class WholeVndTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "1000",
            "1000.00",
            "0.00",
            "9999999999999999.00"
    })
    void shouldDistinguishWholeValueFromItsDatabaseScale(String value) {
        assertThat(WholeVnd.isWhole(new BigDecimal(value))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1000.50",
            "1000.001",
            "0.01"
    })
    void shouldRejectAnyRealFractionWithoutRounding(String value) {
        BigDecimal amount = new BigDecimal(value);

        BusinessException exception = assertThrows(BusinessException.class, () -> WholeVnd.requirePositiveAmount(amount));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AMOUNT_OUT_OF_RANGE);
        assertThat(amount.toPlainString()).isEqualTo(value);
    }

    @Test
    void shouldAllowZeroBalanceAndRequireReconciliationForLegacyFraction() {
        WholeVnd.requireWritableBalance(new BigDecimal("0.00"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> WholeVnd.requireWritableBalance(new BigDecimal("1000.50")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LEGACY_FRACTIONAL_MONEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",
            "-1",
            "10000000000000000"
    })
    void shouldRejectNonpositiveOrOutOfRangeAmounts(String value) {
        assertThrows(BusinessException.class, () -> WholeVnd.requirePositiveAmount(new BigDecimal(value)));
    }
}
