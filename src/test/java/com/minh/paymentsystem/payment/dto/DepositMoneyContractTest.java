package com.minh.paymentsystem.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DepositMoneyContractTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "10000",
            "50000000",
            "10000.00",
            "1e4"
    })
    void shouldAcceptOnlyNumericWholeValuesWithinInclusiveLimits(String value) throws Exception {
        DepositRequest request = mapper.readValue("{\"amount\":" + value + "}", DepositRequest.class);
        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.amount().scale()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"10000\"",
            "10000.50",
            "true",
            "{}",
            "[]",
            "1e999999"
    })
    void shouldRejectStringsFractionsAndOtherTokenTypes(String value) {
        assertThrows(JsonProcessingException.class, () -> mapper.readValue("{\"amount\":" + value + "}", DepositRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "9999",
            "50000001",
            "0",
            "-10000",
            "null"
    })
    void shouldRejectInvalidAmountBeforeCreatingAnOrder(String value) throws Exception {
        DepositRequest request = mapper.readValue("{\"amount\":" + value + "}", DepositRequest.class);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "userId",
            "paymentUrl",
            "returnUrl"
    })
    void shouldRejectClientSuppliedOwnershipAndRedirectFields(String field) {
        assertThrows(JsonProcessingException.class,
                () -> mapper.readValue("{\"amount\":10000,\"" + field + "\":\"untrusted\"}", DepositRequest.class));
    }
}
