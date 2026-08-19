package com.minh.paymentsystem.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VnpayCallbackResponse(
        @JsonProperty("RspCode") String rspCode,
        @JsonProperty("Message") String message
) {}
