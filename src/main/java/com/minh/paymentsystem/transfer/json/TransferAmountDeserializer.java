package com.minh.paymentsystem.transfer.json;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class TransferAmountDeserializer extends JsonDeserializer<BigDecimal> {

    private static final Pattern DECIMAL = Pattern.compile("[0-9]{1,16}(\\.[0-9]{1,2})?");

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING) || !DECIMAL.matcher(parser.getText()).matches()) {
            return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
        }
        return new BigDecimal(parser.getText());
    }
}
