package com.minh.paymentsystem.transfer.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transfer.json.TransferAmountDeserializer;
import com.minh.paymentsystem.transfer.json.TransferAmountSerializer;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TransferRequest(
        @Schema(description = "Recipient email, trimmed without changing case", maxLength = 255, example = "recipient@example.com")
        @NotBlank(message = "Recipient email must not be blank")
        @Email(message = "Invalid recipient email")
        @Size(max = 255, message = "Recipient email must not exceed 255 characters")
        String toEmail,

        @Schema(description = "Whole VND amount as an integer string from 1000 to 50000000 inclusive; no decimal point, signs, whitespace, grouping or exponent", type = "string", example = "1000", pattern = "[0-9]{1,16}")
        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "1000", message = "Amount must be at least 1000 VND")
        @DecimalMax(value = "50000000", message = "Amount must not exceed 50000000 VND")
        @Digits(integer = 16, fraction = 0, message = "Amount must be a whole VND value with at most 16 integer digits")
        @JsonDeserialize(using = TransferAmountDeserializer.class)
        @JsonSerialize(using = TransferAmountSerializer.class)
        BigDecimal amount,

        @Schema(description = "Optional description; null, empty string and whitespace are preserved", maxLength = 255, example = "Transfer for lunch")
        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description) {

    public TransferRequest {
        if (toEmail != null) {
            toEmail = toEmail.trim();
        }
    }
}
