package com.minh.paymentsystem.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class VnpayGatewayServiceImplTest {

    private static final String CALLBACK_HASH = "74f277b7349ed844f7fce71a365fd9c47087e780ba1050687f1f96f3da393492c6d654bf16852e6c9cc85a920972861d26ba9db99e73fab5a0cdb8a4b58b3ee3";
    private final VnpayGatewayServiceImpl gateway = new VnpayGatewayServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(gateway, "vnpTmnCode", "TESTCODE");
        ReflectionTestUtils.setField(gateway, "vnpHashSecret", "TESTSECRET");
        ReflectionTestUtils.setField(gateway, "vnpUrl", "https://sandbox.example.com/pay");
        ReflectionTestUtils.setField(gateway, "vnpReturnUrl", "http://localhost/return");
    }

    @Test
    void shouldBuildEncodedPaymentUrlWithMatchingSignature() throws Exception {
        String url = gateway.buildPaymentUrl("ORDER 1&x", new BigDecimal("50000"), "127.0.0.1");

        assertThat(url).startsWith("https://sandbox.example.com/pay?");
        String rawQuery = URI.create(url).getRawQuery();
        int signatureOffset = rawQuery.lastIndexOf("&vnp_SecureHash=");
        String signedData = rawQuery.substring(0, signatureOffset);
        Map<String, String> parameters = Arrays.stream(rawQuery.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.US_ASCII),
                        pair -> URLDecoder.decode(pair[1], StandardCharsets.US_ASCII)));

        assertThat(parameters).containsEntry("vnp_Amount", "5000000")
                .containsEntry("vnp_TxnRef", "ORDER 1&x")
                .containsEntry("vnp_OrderInfo", "Nap tien vao vi ORDER 1&x")
                .containsEntry("vnp_TmnCode", "TESTCODE")
                .containsEntry("vnp_ReturnUrl", "http://localhost/return")
                .containsEntry("vnp_IpAddr", "127.0.0.1")
                .containsEntry("vnp_Version", "2.1.0")
                .containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_Locale", "vn")
                .containsEntry("vnp_CurrCode", "VND")
                .containsEntry("vnp_OrderType", "topup");
        assertThat(parameters.get("vnp_CreateDate")).matches("\\d{14}");
        assertThat(signedData).contains("vnp_TxnRef=ORDER+1%26x");
        assertThat(parameters.get("vnp_SecureHash")).isEqualTo(sign(signedData));
    }

    @Test
    void shouldVerifyKnownCallbackWithoutMutatingInput() {
        Map<String, String> parameters = Map.of(
                "vnp_TxnRef", "ORDER_1",
                "vnp_Amount", "5000000",
                "vnp_OrderInfo", "Nap tien vao vi ORDER_1",
                "vnp_SecureHashType", "SHA512",
                "vnp_SecureHash", CALLBACK_HASH.toUpperCase(Locale.ROOT));

        assertThat(gateway.verifyChecksum(parameters)).isTrue();
        assertThat(parameters).hasSize(5).containsKey("vnp_SecureHashType");
    }

    @Test
    void shouldRejectFractionalPaymentAmountRatherThanTruncateIt() {
        assertThatThrownBy(() -> gateway.buildPaymentUrl("ORDER_1", new BigDecimal("10000.001"), "127.0.0.1"))
                .isInstanceOf(com.minh.paymentsystem.common.exception.BusinessException.class);
    }

    @Test
    void shouldEncodeWholeDatabaseScaleWithoutDecimalZeros() {
        String url = gateway.buildPaymentUrl("ORDER_1", new BigDecimal("10000.00"), "127.0.0.1");

        assertThat(url).contains("vnp_Amount=1000000&");
    }

    @Test
    void shouldRejectTamperedCallback() {
        Map<String, String> parameters = Map.of(
                "vnp_TxnRef", "ORDER_1",
                "vnp_Amount", "5000001",
                "vnp_OrderInfo", "Nap tien vao vi ORDER_1",
                "vnp_SecureHash", CALLBACK_HASH);

        assertThat(gateway.verifyChecksum(parameters)).isFalse();
    }

    @Test
    void shouldRejectCallbackWithoutSignature() {
        assertThat(gateway.verifyChecksum(Map.of("vnp_TxnRef", "ORDER_1"))).isFalse();
    }

    @Test
    void shouldRejectBlankSignaturesEvenWhenSigningConfigurationIsInvalid() {
        ReflectionTestUtils.setField(gateway, "vnpHashSecret", "");

        assertThat(gateway.verifyChecksum(Map.of("vnp_SecureHash", ""))).isFalse();
        assertThat(gateway.verifyChecksum(Map.of("vnp_SecureHash", "   "))).isFalse();
    }

    @Test
    void shouldFailUrlCreationWhenSigningFails() {
        ReflectionTestUtils.setField(gateway, "vnpHashSecret", "");

        assertThatThrownBy(() -> gateway.buildPaymentUrl("ORDER_1", new BigDecimal("50000"), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPropagateSigningFailureDuringVerification() {
        ReflectionTestUtils.setField(gateway, "vnpHashSecret", "");

        assertThatThrownBy(() -> gateway.verifyChecksum(Map.of("vnp_TxnRef", "ORDER_1", "vnp_SecureHash", CALLBACK_HASH)))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldPreserveExistingSeparatorHandlingForEmptyAndNullValues() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("a", "one");
        parameters.put("m", null);
        parameters.put("z", "");
        // Existing code signs "a=one&" when later sorted fields are empty.
        parameters.put("vnp_SecureHash", "8c473410eef45db5401c5f68f52e10271e35f5d0f2c0d31adebfb0498c2e9c36de6867f7e1e975a78dbdaf3bb350919ddc131a912d251f03475c39923ef7488c");

        assertThat(gateway.verifyChecksum(parameters)).isTrue();
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec("TESTSECRET".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
