package com.minh.paymentsystem.transfer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecipientLookupRequest(
        @NotBlank
        @Email
        @Size(max = 255)
        String email) {
    public RecipientLookupRequest {
        email = email == null ? null : email.trim();
    }
}
