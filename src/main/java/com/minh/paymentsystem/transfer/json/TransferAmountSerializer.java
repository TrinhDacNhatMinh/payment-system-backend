package com.minh.paymentsystem.transfer.json;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import com.minh.paymentsystem.common.validation.WholeVnd;

public class TransferAmountSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        // Preserve fractional legacy snapshots exactly; whole values never need decimal zeros.
        generator.writeString(WholeVnd.isWhole(value) ? value.toBigIntegerExact().toString() : value.toPlainString());
    }
}
