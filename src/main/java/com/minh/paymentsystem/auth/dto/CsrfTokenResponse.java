package com.minh.paymentsystem.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(
        @Schema(description = "Header name to use for the next auth POST", example = "X-XSRF-TOKEN")
        String headerName,

        @Schema(description = "Masked CSRF token to send in the specified header", example = "<masked-csrf-token>")
        String token) {

    public static CsrfTokenResponse from(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }
}
