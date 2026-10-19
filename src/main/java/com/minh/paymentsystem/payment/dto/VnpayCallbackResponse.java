package com.minh.paymentsystem.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record VnpayCallbackResponse(
        @Schema(description = "Response code from VNPay", example = "00")
        @JsonProperty("RspCode")
        String rspCode,

        @Schema(description = "Message from VNPay", example = "Confirm Success")
        @JsonProperty("Message")
        String message) {
}
