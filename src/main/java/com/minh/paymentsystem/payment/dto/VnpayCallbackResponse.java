package com.minh.paymentsystem.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayCallbackResponse(
        @Schema(description = "Response code from VNPay", example = "00")
        @JsonProperty("RspCode") String rspCode,

        @Schema(description = "Message from VNPay", example = "Confirm Success")
        @JsonProperty("Message") String message
) {}
