package com.minh.paymentsystem.transaction.controller;

import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.transaction.dto.AdminTransactionFilterRequest;
import com.minh.paymentsystem.transaction.dto.TransactionResponse;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transaction.enums.TransactionType;
import com.minh.paymentsystem.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/transactions")
@RequiredArgsConstructor
@Tag(name = "Admin Transactions", description = "Admin API for monitoring all transactions")
public class AdminTransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Get all transactions", description = "Retrieve a paginated and filtered list of all transactions in the system")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved transactions"),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Admin access required")
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<PageResponse<TransactionResponse>>> getAllTransactions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        AdminTransactionFilterRequest filter = new AdminTransactionFilterRequest(
                userId, email, type, status, fromDate, toDate
        );

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PageResponse<TransactionResponse> response = transactionService.getAllTransactions(filter, pageable);

        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(response));
    }
}
