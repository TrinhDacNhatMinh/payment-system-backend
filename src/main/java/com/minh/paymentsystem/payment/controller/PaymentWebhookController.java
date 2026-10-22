package com.minh.paymentsystem.payment.controller;

import java.net.URI;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.minh.paymentsystem.payment.dto.VnpayCallbackResponse;
import com.minh.paymentsystem.payment.service.PaymentService;

@RestController
@RequestMapping("/api/v1/payment")
@Tag(name = "Payment Webhook", description = "Endpoints for VNPay IPN and Return URLs")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @Operation(summary = "VNPay IPN Webhook", description = "Validates unique query parameters, checksum, merchant, stored amount and gateway status. Locks the order with NOWAIT and commits wallet credit, DEPOSIT transaction, status and verified metadata atomically. Link expiry does not reject a valid late IPN. Returns the VNPay RspCode/Message protocol, not BaseResponse.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "RspCode: 00 committed, 02 terminal, 01 missing order, 04 amount mismatch, 97 checksum invalid, 99 invalid payload/busy/rollback")
    })
    @GetMapping("/vnpay-webhook")
    public ResponseEntity<VnpayCallbackResponse> handleVnpayWebhook(@RequestParam MultiValueMap<String, String> params) {
        VnpayCallbackResponse response = paymentService.handleCallback(params);
        return ResponseEntity
                .status(HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @Operation(summary = "VNPay Return URL", description = "Verifies return data and redirects to the configured frontend result with orderId and the signed callback in a URL fragment. The frontend forwards this callback to the IPN endpoint, then performs an authenticated order lookup. Invalid returns never forward callback data. This return handler does not credit the wallet itself.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "303", description = "See Other: trusted frontend result URL; query is not proof of payment")
    })
    @GetMapping("/vnpay-return")
    public ResponseEntity<Void> handleVnpayReturn(@RequestParam MultiValueMap<String, String> params) {
        URI result = paymentService.processReturn(params);
        return ResponseEntity
                .status(HttpStatus.SEE_OTHER)
                .location(result)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
