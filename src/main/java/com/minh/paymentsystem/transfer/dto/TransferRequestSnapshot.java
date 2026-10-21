package com.minh.paymentsystem.transfer.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transfer.json.TransferAmountSerializer;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Read-only stored payload; fractional amounts can appear only in legacy requests and must never be rounded or resubmitted as a new integer amount")
public record TransferRequestSnapshot(
        String toEmail,
        @Schema(type = "string", description = "Whole VND string, or the exact historical fractional amount when legacyFractional is true", example = "1000")
        @JsonSerialize(using = TransferAmountSerializer.class)
        BigDecimal amount,
        String description) {

    public static TransferRequestSnapshot from(TransferRequest request) {
        return new TransferRequestSnapshot(request.toEmail(), request.amount(), request.description());
    }
}
