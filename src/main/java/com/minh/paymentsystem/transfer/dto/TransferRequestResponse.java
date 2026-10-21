package com.minh.paymentsystem.transfer.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import com.minh.paymentsystem.transfer.enums.TransferRequestState;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TransferRequestResponse(
        UUID idempotencyKey,
        TransferRequestState state,
        TransferRequestSnapshot request,
        TransferResponse result,
        TransferRequestError error,
        @Schema(description = "True when stored request or receipt contains a non-integer value; read-only legacy data requiring reconciliation")
        boolean legacyFractional) {
}
