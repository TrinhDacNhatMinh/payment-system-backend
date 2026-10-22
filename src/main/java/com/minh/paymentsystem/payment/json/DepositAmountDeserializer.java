package com.minh.paymentsystem.payment.json;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class DepositAmountDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT) && !parser.hasToken(JsonToken.VALUE_NUMBER_FLOAT)) {
            return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
        }
        BigDecimal amount = parser.getDecimalValue().stripTrailingZeros();
        if (amount.scale() > 0 || amount.scale() < -16) {
            return (BigDecimal) context.handleWeirdNumberValue(BigDecimal.class, amount, "Deposit amount must be a whole VND value");
        }
        return amount.setScale(0);
    }
}
