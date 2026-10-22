package com.minh.paymentsystem.transfer.controller;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.transfer.dto.RecipientLookupRequest;
import com.minh.paymentsystem.transfer.dto.RecipientLookupResponse;
import com.minh.paymentsystem.transfer.service.RecipientLookupService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transfer/recipient")
public class TransferRecipientController {
    private final RecipientLookupService recipientLookupService;

    @Operation(summary = "Check recipient email existence", description = "Authenticated read-only lookup. Trims email, preserves case, maximum 255 characters. Returns only exists=true/false, without account details. This is advisory: transfer still validates the recipient, account status and balance atomically at submission.")
    @GetMapping
    public ResponseEntity<BaseResponse<RecipientLookupResponse>> lookup(
            @Valid @ModelAttribute RecipientLookupRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.success(recipientLookupService.lookup(request)));
    }
}
