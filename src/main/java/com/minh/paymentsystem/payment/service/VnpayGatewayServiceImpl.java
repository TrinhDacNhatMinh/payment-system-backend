package com.minh.paymentsystem.payment.service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.minh.paymentsystem.common.validation.WholeVnd;

@Service
@Slf4j
public class VnpayGatewayServiceImpl implements PaymentGatewayService {

    private static final DateTimeFormatter GATEWAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${vnpay.tmn-code:VNPAY001}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret:SECRETKEY}")
    private String vnpHashSecret;

    @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/v1/payment/vnpay-return}")
    private String vnpReturnUrl;

    @Override
    public String buildPaymentUrl(String orderId, BigDecimal amount, String ipAddress, LocalDateTime createdAt, LocalDateTime expiresAt) {
        WholeVnd.requirePositiveAmount(amount);
        log.debug("Building payment URL for order {}, amount {}", orderId, amount);
        Map<String, String> vnpParams = createPaymentParameters(orderId, amount, ipAddress, createdAt, expiresAt);
        return vnpUrl + "?" + buildSignedQuery(vnpParams);
    }

    @Override
    public boolean matchesMerchant(Map<String, String> params) {
        return vnpTmnCode.equals(params.get("vnp_TmnCode"))
                && (!params.containsKey("vnp_CurrCode") || "VND".equals(params.get("vnp_CurrCode")));
    }

    @Override
    public boolean verifyChecksum(Map<String, String> params) {
        String vnpSecureHash = params.get("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isBlank()) {
            return false;
        }

        Map<String, String> signParams = new HashMap<>(params);
        signParams.remove("vnp_SecureHash");
        signParams.remove("vnp_SecureHashType");

        String hashData = buildVerificationData(signParams);
        String calculatedHash = signWithHmacSha512(vnpHashSecret, hashData);
        return calculatedHash.equalsIgnoreCase(vnpSecureHash);
    }

    private Map<String, String> createPaymentParameters(String orderId, BigDecimal amount, String ipAddress,
            LocalDateTime createdAt, LocalDateTime expiresAt) {
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpTmnCode);
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", orderId);
        vnpParams.put("vnp_OrderInfo", "Nap tien vao vi " + orderId);
        vnpParams.put("vnp_OrderType", "topup");

        // Amount must be multiplied by 100 according to VNPay specs
        BigDecimal amountInVnPayFormat = amount.multiply(new BigDecimal("100"));
        vnpParams.put("vnp_Amount", amountInVnPayFormat.toBigIntegerExact().toString());

        vnpParams.put("vnp_ReturnUrl", vnpReturnUrl);
        vnpParams.put("vnp_IpAddr", ipAddress);

        vnpParams.put("vnp_CreateDate", createdAt.format(GATEWAY_DATE_FORMAT));
        vnpParams.put("vnp_ExpireDate", expiresAt.format(GATEWAY_DATE_FORMAT));
        return vnpParams;
    }

    private String buildSignedQuery(Map<String, String> vnpParams) {
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if ((fieldValue != null) && !fieldValue.isEmpty()) {
                appendPaymentParameter(fieldName, fieldValue, hashData, query);
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnpSecureHash = signWithHmacSha512(vnpHashSecret, hashData.toString());

        return queryUrl + "&vnp_SecureHash=" + vnpSecureHash;
    }

    private void appendPaymentParameter(String fieldName, String fieldValue, StringBuilder hashData, StringBuilder query) {
        String encodedFieldName = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
        String encodedFieldValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);

        hashData.append(encodedFieldName);
        hashData.append('=');
        hashData.append(encodedFieldValue);

        query.append(encodedFieldName);
        query.append('=');
        query.append(encodedFieldValue);
    }

    private String buildVerificationData(Map<String, String> signParams) {
        List<String> fieldNames = new ArrayList<>(signParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();

        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = signParams.get(fieldName);
            if ((fieldValue != null) && !fieldValue.isEmpty()) {
                hashData.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));

                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        return hashData.toString();
    }

    private String signWithHmacSha512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] result = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not sign VNPay payment data", ex);
        }
    }
}
