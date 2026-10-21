package com.minh.paymentsystem.transfer.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;

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
            "1001",
            "50000000"
    })
    void shouldAcceptOnlyIntegerStrings(String amount) throws Exception {
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
            "1000.00",
            "\"1000.0\"",
            "\"1000.00\"",
            "\"1000.50\"",
            "\"+1000\"",
            "\"-1000\"",
            "\" 1000\"",
            "\"1000 \"",
            "\"1e3\"",
            "\"1,000\"",
            "\"10000000000000000\""
    })
    void shouldRejectNumbersDecimalPointsAndUnsupportedFormats(String jsonValue) {
        assertThrows(JsonProcessingException.class, () -> mapper.readValue(
                "{\"toEmail\":\"recipient@example.com\",\"amount\":" + jsonValue + "}", TransferRequest.class));
    }

    @Test
    void shouldRejectFractionalValuesAtBothRequestBoundaries() {
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("1000.50"), null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new DepositRequest(new BigDecimal("10000.50")))).isNotEmpty();
    }

    @Test
    void shouldEnforceMinimumAndDescriptionLimitWithoutTrimmingDescription() {
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("999"), null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("50000001"), null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("9999999999999999"), null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("bad-email", new BigDecimal("1000"), null))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("1000"), "a".repeat(256)))).isNotEmpty();
        assertThat(VALIDATOR.validate(new TransferRequest("recipient@example.com", new BigDecimal("1000"), "a".repeat(255)))).isEmpty();
    }

    @Test
    void shouldSerializeStoredWholeAmountsWithoutDecimalZeros() throws Exception {
        TransferResponse receipt = new TransferResponse(1L, new BigDecimal("0.00"), new BigDecimal("1000.00"),
                TransactionStatus.SUCCESS, LocalDateTime.now());

        var json = mapper.readTree(mapper.writeValueAsString(receipt));

        assertThat(json.get("fromWalletBalance").isTextual()).isTrue();
        assertThat(json.get("fromWalletBalance").asText()).isEqualTo("0");
        assertThat(json.get("amount").asText()).isEqualTo("1000");
    }

    @Test
    void shouldPreserveMaximumTransferAmountExactly() throws Exception {
        var request = new TransferRequest("recipient@example.com", new BigDecimal("50000000"), null);

        assertThat(mapper.readTree(mapper.writeValueAsString(request)).get("amount").asText())
                .isEqualTo("50000000");
    }

    @Test
    void shouldReadHistoricalReceiptAboveNewTransferLimitWithoutCappingBalance() throws Exception {
        TransferResponse receipt = new TransferResponse(1L, new BigDecimal("9999999999999999.00"), new BigDecimal("50000001.00"),
                TransactionStatus.SUCCESS, LocalDateTime.now());

        var json = mapper.readTree(mapper.writeValueAsString(receipt));

        assertThat(json.get("amount").asText()).isEqualTo("50000001");
        assertThat(json.get("fromWalletBalance").asText()).isEqualTo("9999999999999999");
    }

    @Test
    void shouldKeepFractionalLegacySnapshotExactAndMarkedReadOnly() throws Exception {
        TransferResponse receipt = new TransferResponse(1L, new BigDecimal("8999.50"), new BigDecimal("1000.50"),
                TransactionStatus.SUCCESS, LocalDateTime.now());
        TransferRequestResponse lookup = new TransferRequestResponse(UUID.randomUUID(), TransferRequestState.SUCCEEDED,
                new TransferRequestSnapshot("recipient@example.com", new BigDecimal("1000.50"), null), receipt, null, true);

        var json = mapper.readTree(mapper.writeValueAsString(lookup));

        assertThat(json.get("legacyFractional").asBoolean()).isTrue();
        assertThat(json.at("/request/amount").asText()).isEqualTo("1000.50");
        assertThat(json.at("/result/amount").asText()).isEqualTo("1000.50");
        assertThat(json.at("/result/fromWalletBalance").asText()).isEqualTo("8999.50");
        assertThat(receipt.amount().toPlainString()).isEqualTo("1000.50");
    }
}
