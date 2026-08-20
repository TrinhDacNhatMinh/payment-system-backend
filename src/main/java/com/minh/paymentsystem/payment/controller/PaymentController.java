package com.minh.paymentsystem.payment.controller;

import com.minh.paymentsystem.auth.security.CustomUserDetails;
import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.payment.dto.DepositRequest;
import com.minh.paymentsystem.payment.dto.DepositResponse;
import com.minh.paymentsystem.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "VNPay Payment API endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Create deposit order", description = "Creates a VNPay deposit order and returns the payment URL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully created deposit order"),
            @ApiResponse(responseCode = "400", description = "Validation error or amount out of allowed range"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token")
    })
    @PostMapping("/deposit")
    public ResponseEntity<BaseResponse<DepositResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpServletRequest) {

        // Extract client IP address for VNPay requirement
        String ipAddress = httpServletRequest.getRemoteAddr();

        DepositResponse response = paymentService.createDepositOrder(userDetails.getUserId(), request, ipAddress);
        return ResponseEntity.status(HttpStatus.OK).body(BaseResponse.success(response));
    }
}
