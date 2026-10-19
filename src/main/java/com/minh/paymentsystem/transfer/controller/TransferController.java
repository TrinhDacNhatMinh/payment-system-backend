package com.minh.paymentsystem.transfer.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minh.paymentsystem.auth.security.CustomUserDetails;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.service.TransferService;

@RestController
@RequestMapping("/api/v1/transfer")
@Tag(name = "Transfer", description = "Money Transfer API endpoints")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @Operation(summary = "Transfer money", description = "Transfer money from current user's wallet to another user's wallet via email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully transferred money"),
            @ApiResponse(responseCode = "400", description = "Validation error, insufficient balance, or self transfer not allowed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Recipient not found"),
            @ApiResponse(responseCode = "409", description = "Concurrent modification error")
    })
    @PostMapping
    public ResponseEntity<BaseResponse<TransferResponse>> transfer(
            @RequestBody @Valid TransferRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TransferResponse response = transferService.transfer(userDetails.getUserId(), request);
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(BaseResponse.success(response));
    }
}
