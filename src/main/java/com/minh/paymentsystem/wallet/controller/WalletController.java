package com.minh.paymentsystem.wallet.controller;

import com.minh.paymentsystem.auth.security.CustomUserDetails;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.wallet.dto.WalletResponse;
import com.minh.paymentsystem.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Wallet API endpoints")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Get my wallet", description = "Retrieves the wallet information for the currently authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved wallet information"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<WalletResponse>> getMyWallet(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        WalletResponse wallet = walletService.getMyWallet(userDetails.getUserId());
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(wallet));
    }
}
