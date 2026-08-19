package com.minh.paymentsystem.payment.controller;

import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
    @GetMapping("/vnpay-webhook")
    public ResponseEntity<VnpayCallbackResponse> handleVnpayWebhook(@RequestParam Map<String, String> params) {
        VnpayCallbackResponse response = paymentService.handleCallback(params);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "VNPay Return URL", description = "Redirect URL for users after completing payment on VNPay")
    @GetMapping("/vnpay-return")
    public ResponseEntity<String> handleVnpayReturn(@RequestParam Map<String, String> params) {
        String result = paymentService.processReturn(params);
        return ResponseEntity.ok(result);
    }
}
