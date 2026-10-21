package com.minh.paymentsystem.transfer.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.minh.paymentsystem.transaction.enums.TransactionStatus;

class TransferMoneyContractTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1000",
            "1000.5",
            "1000.50",
            "9999999999999999.99"
    })
    void shouldAcceptExactDecimalStrings(String amount) throws Exception {
        TransferRequest request = mapper.readValue(
                "{\"toEmail\":\" Recipient@example.com \",\"amount\":\"" + amount + "\",\"description\":\"  \"}", TransferRequest.class);

        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.toEmail()).isEqualTo("Recipient@example.com");
        assertThat(request.description()).isEqualTo("  ");
        assertThat(request.amount()).isEqualByComparingTo(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1000",
            "1000.50",
            "\"+1000\"",
            "\"-1000\"",
            "\" 1000\"",
            "\"1000 \"",
            "\"1e3\"",
            "\"1,000\"",
            "\"1000.001\"",
            "\"10000000000000000\""
    })
    void shouldRejectNumbersAndUnsupportedDecimalFormats(String jsonValue) {
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(
                "{\"toEmail\":\"recipient@example.com\",\"amount\":" + jsonValue + "}", TransferRequest.class));
    }

    @Test
    void shouldEnforceMinimumAndDescriptionLimitWithoutTrimmingDescription() {
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("999.99"), null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("bad-email", new BigDecimal("1000"), null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("1000"), "a".repeat(256)))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("1000"), "a".repeat(255)))).isEmpty();
    }

    @Test
    void shouldSerializeZeroBalanceAndFractionalMoneyAsStrings() throws Exception {
        TransferResponse receipt = new TransferResponse(1L, BigDecimal.ZERO, new BigDecimal("1000.50"),
                TransactionStatus.SUCCESS, LocalDateTime.now());

        var json = mapper.readTree(mapper.writeValueAsString(receipt));

        assertThat(json.get("fromWalletBalance").isTextual()).isTrue();
        assertThat(json.get("fromWalletBalance").asText()).isEqualTo("0");
        assertThat(json.get("amount").asText()).isEqualTo("1000.50");
    }

    @Test
    void shouldPreserveLargeValuesThroughLookupPayloadAndReceipt() throws Exception {
        BigDecimal amount = new BigDecimal("9999999999999999.99");
        var request = new TransferRequest("recipient@example.com", amount, null);

        assertThat(mapper.readTree(mapper.writeValueAsString(request)).get("amount").asText())
                .isEqualTo("9999999999999999.99");
    }
}
