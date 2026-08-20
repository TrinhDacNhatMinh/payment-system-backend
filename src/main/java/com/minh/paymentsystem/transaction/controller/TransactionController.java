package com.minh.paymentsystem.transaction.controller;

import com.minh.paymentsystem.auth.security.CustomUserDetails;
import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.transaction.dto.TransactionFilterRequest;
import com.minh.paymentsystem.transaction.dto.TransactionResponse;
import com.minh.paymentsystem.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction", description = "Transaction History APIs")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Get my transaction history", description = "Retrieve a paginated and filtered list of user's transactions")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved transaction history"),
            @ApiResponse(responseCode = "400", description = "Validation error for pagination/filter parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Wallet not found for user")
    })
    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> getMyTransactions(
            @ModelAttribute TransactionFilterRequest filter,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        PageResponse<TransactionResponse> response = transactionService.getMyTransactions(userDetails.getUserId(), filter);
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(response));
    }
}
