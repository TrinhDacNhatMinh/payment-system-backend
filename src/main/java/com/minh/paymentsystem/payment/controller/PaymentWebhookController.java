package com.minh.paymentsystem.payment.controller;

import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Tag(name = "Payment Webhook", description = "Endpoints for VNPay IPN and Return URLs")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @Operation(summary = "VNPay IPN Webhook", description = "Server-to-server callback from VNPay to update order status")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully processed webhook"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment order or wallet not found")
    })
    @GetMapping("/vnpay-webhook")
    public ResponseEntity<VnpayCallbackResponse> handleVnpayWebhook(@RequestParam Map<String, String> params) {
        VnpayCallbackResponse response = paymentService.handleCallback(params);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Operation(summary = "VNPay Return URL", description = "Redirect URL for users after completing payment on VNPay")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully processed return URL"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Payment order not found")
    })
    @GetMapping("/vnpay-return")
    public ResponseEntity<String> handleVnpayReturn(@RequestParam Map<String, String> params) {
        String result = paymentService.processReturn(params);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
