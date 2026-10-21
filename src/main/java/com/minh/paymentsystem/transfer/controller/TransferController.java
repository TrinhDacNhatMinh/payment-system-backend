package com.minh.paymentsystem.transfer.controller;

import java.util.List;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minh.paymentsystem.auth.security.user.CustomUserDetails;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferRequestResponse;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.security.IdempotencyKey;
import com.minh.paymentsystem.transfer.service.TransferService;

@RestController
@RequestMapping("/api/v1/transfer")
@Tag(name = "Transfer", description = "Money Transfer API endpoints")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Operation(summary = "Transfer money", description = "Requires exactly one UUID v4 Idempotency-Key scoped to the authenticated sender. Same key/payload replays the stored receipt or rejection. Different payload returns IDEMPOTENCY_KEY_REUSED; a locked request returns TRANSFER_IN_PROGRESS. Transfer money and the receipt commit atomically. Monetary fields use exact decimal JSON strings. On timeout or 5xx, query the same key; do not create a new key or automatically retry.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully transferred money"),
            @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_INVALID, validation error, or stored business rejection"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Account locked or access denied"),
            @ApiResponse(responseCode = "404", description = "Recipient not found"),
            @ApiResponse(responseCode = "409", description = "IDEMPOTENCY_KEY_REUSED, TRANSFER_IN_PROGRESS, or CONCURRENT_MODIFICATION"),
            @ApiResponse(responseCode = "500", description = "Unknown outcome; query the request with the same key")
    })
    @PostMapping
    public ResponseEntity<BaseResponse<TransferResponse>> transfer(
            @Parameter(required = true, description = "Exactly one UUID v4 identifying this reviewed transfer intent; reuse unchanged for recovery") @RequestHeader(name = IdempotencyKey.HEADER_NAME, required = false) List<String> keys,
            @RequestBody @Valid TransferRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TransferResponse response = transferService.transfer(userDetails.getUserId(), keys, request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Look up a transfer request", description = "Reads only the authenticated sender's request. Returns canonical payload, PENDING/SUCCEEDED/REJECTED and the stored receipt or rejection. This GET never executes or resumes a transfer. A 404 does not prove an earlier POST failed or will not arrive.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Request state and any stored outcome"),
            @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_INVALID"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
            @ApiResponse(responseCode = "403", description = "Account locked or access denied"),
            @ApiResponse(responseCode = "404", description = "TRANSFER_REQUEST_NOT_FOUND, including keys owned by another user")
    })
    @GetMapping("/requests/{idempotencyKey}")
    public ResponseEntity<BaseResponse<TransferRequestResponse>> getRequest(
            @Parameter(description = "UUID v4 of a transfer submitted by the current user") @PathVariable String idempotencyKey,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TransferRequestResponse response = transferService.getRequest(userDetails.getUserId(), idempotencyKey);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(response));
    }
}
