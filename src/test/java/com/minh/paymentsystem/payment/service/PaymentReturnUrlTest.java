package com.minh.paymentsystem.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class PaymentReturnUrlTest {

    @Test
    void shouldAllowLoopbackHttpOnlyInDevelopment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        PaymentReturnUrl url = new PaymentReturnUrl("http://localhost:5173/payment/result", environment);
        assertThat(url.forOrder("ORDER_1").toString()).isEqualTo("http://localhost:5173/payment/result?orderId=ORDER_1");
        assertThat(url.forOrder("ORDER_1&status=success").toString())
                .isEqualTo("http://localhost:5173/payment/result?verification=invalid");
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentReturnUrl("http://localhost:5173/payment/result", new MockEnvironment()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://untrusted.example/payment/result",
            "https://trusted.example/other",
            "https://trusted.example/payment/result?target=evil",
            "https://user@trusted.example/payment/result",
            "//evil.example/payment/result",
            "https://trusted.example/payment/result#token"
    })
    void shouldRejectUnsafeConfiguredRedirects(String value) {
        assertThrows(IllegalArgumentException.class, () -> new PaymentReturnUrl(value, new MockEnvironment()));
    }
}
