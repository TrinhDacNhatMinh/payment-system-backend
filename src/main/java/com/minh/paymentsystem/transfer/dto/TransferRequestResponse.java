package com.minh.paymentsystem.transfer.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.minh.paymentsystem.transfer.enums.TransferRequestState;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TransferRequestResponse(
        UUID idempotencyKey,
        TransferRequestState state,
        TransferRequest request,
        TransferResponse result,
        TransferRequestError error) {
}
