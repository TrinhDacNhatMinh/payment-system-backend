package com.minh.paymentsystem.payment.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
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
import com.minh.paymentsystem.payment.dto.DepositOrderResponse;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.service.PaymentService;
import com.minh.paymentsystem.transfer.security.IdempotencyKey;

@RestController
@RequestMapping("/api/v1/payment")
@Tag(name = "Payment", description = "VNPay Payment API endpoints")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create or replay deposit order", description = "Requires exactly one UUID v4 Idempotency-Key. Amount is a whole VND JSON number from 10000 to 50000000 inclusive; JSON strings are rejected. Same sender/key/amount returns the same order and original signed link, never extends the 15-minute expiry. Different amount returns DEPOSIT_KEY_REUSED. Creation does not credit the wallet. Expiry hides paymentUrl without marking the order FAILED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully created deposit order"),
            @ApiResponse(responseCode = "400", description = "Validation error or amount out of allowed range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Account locked or access denied"),
            @ApiResponse(responseCode = "409", description = "DEPOSIT_KEY_REUSED or DEPOSIT_REQUEST_IN_PROGRESS")
    })
    @PostMapping("/deposit")
    public ResponseEntity<BaseResponse<DepositOrderResponse>> deposit(
            @Parameter(description = "Exactly one UUID v4 identifying this deposit intent", required = true) @RequestHeader(name = IdempotencyKey.HEADER_NAME, required = false) List<String> keys,
            @RequestBody @Valid DepositRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpServletRequest) {

        // Extract client IP address for VNPay requirement
        String ipAddress = httpServletRequest.getRemoteAddr();

        DepositOrderResponse response = paymentService.createDepositOrder(userDetails.getUserId(), keys, request, ipAddress);
        return ResponseEntity
                .status(HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(response));
    }

    @Operation(summary = "Get my deposit order", description = "Reads only an order owned by the authenticated user. Returns the current status and the original URL only while the link is usable; never creates or completes a payment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current deposit order view"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Account locked or access denied"),
            @ApiResponse(responseCode = "404", description = "PAYMENT_ORDER_NOT_FOUND, including another owner's order"),
            @ApiResponse(responseCode = "409", description = "Legacy fractional amount requires reconciliation")
    })
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<BaseResponse<DepositOrderResponse>> getOrder(
            @PathVariable String orderId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(paymentService.getOrder(userDetails.getUserId(), orderId)));
    }

    @Operation(summary = "Look up my deposit request", description = "Reads the owner's order by its UUID v4 deposit key. A 404 does not prove a previous POST was not processed; do not automatically create a new key or order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current deposit order view"),
            @ApiResponse(responseCode = "400", description = "IDEMPOTENCY_KEY_INVALID"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Account locked or access denied"),
            @ApiResponse(responseCode = "404", description = "DEPOSIT_REQUEST_NOT_FOUND, including another owner's key"),
            @ApiResponse(responseCode = "409", description = "Legacy fractional amount requires reconciliation")
    })
    @GetMapping("/deposit/requests/{idempotencyKey}")
    public ResponseEntity<BaseResponse<DepositOrderResponse>> getDepositRequest(
            @PathVariable String idempotencyKey, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(paymentService.getDepositRequest(userDetails.getUserId(), idempotencyKey)));
    }
}
