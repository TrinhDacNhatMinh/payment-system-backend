package com.minh.paymentsystem.payment.service.impl;

import com.minh.paymentsystem.payment.service.PaymentGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Service
public class VnpayGatewayServiceImpl implements PaymentGatewayService {

    @Value("${vnpay.tmn-code:VNPAY001}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret:SECRETKEY}")
    private String vnpHashSecret;

    @Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/v1/payment/vnpay-return}")
    private String vnpReturnUrl;

    @Override
    public String buildPaymentUrl(String orderId, BigDecimal amount, String ipAddress) {
        log.info("Building payment URL for order {}, amount {}", orderId, amount);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpTmnCode);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", orderId);
        vnp_Params.put("vnp_OrderInfo", "Nap tien vao vi " + orderId);
        vnp_Params.put("vnp_OrderType", "topup");
        
        // Amount must be multiplied by 100 according to VNPay specs
        BigDecimal amountInVnPayFormat = amount.multiply(new BigDecimal("100"));
        vnp_Params.put("vnp_Amount", String.valueOf(amountInVnPayFormat.longValue()));
        
        vnp_Params.put("vnp_ReturnUrl", vnpReturnUrl);
        vnp_Params.put("vnp_IpAddr", ipAddress);

        // Date format: yyyyMMddHHmmss with GMT+7
        Date dt = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("GMT+7"));
        String dateString = formatter.format(dt);
        vnp_Params.put("vnp_CreateDate", dateString);

        // Build data to hash and query string
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && !fieldValue.isEmpty()) {
                try {
                    String encodedFieldName = URLEncoder.encode(fieldName, StandardCharsets.US_ASCII);
                    String encodedFieldValue = URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII);
                    
                    // Build hash data
                    hashData.append(encodedFieldName);
                    hashData.append('=');
                    hashData.append(encodedFieldValue);
                    
                    // Build query
                    query.append(encodedFieldName);
                    query.append('=');
                    query.append(encodedFieldValue);
                } catch (Exception e) {
                    log.error("Error encoding URL parameter", e);
                }
                
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = hmacSHA512(vnpHashSecret, hashData.toString());
        
        return vnpUrl + "?" + queryUrl + "&vnp_SecureHash=" + vnp_SecureHash;
    }

    @Override
    public boolean verifyChecksum(Map<String, String> params) {
        String vnp_SecureHash = params.get("vnp_SecureHash");
        if (vnp_SecureHash == null) {
            return false;
        }

        Map<String, String> signParams = new HashMap<>(params);
        signParams.remove("vnp_SecureHash");
        signParams.remove("vnp_SecureHashType");

        List<String> fieldNames = new ArrayList<>(signParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = signParams.get(fieldName);
            if ((fieldValue != null) && !fieldValue.isEmpty()) {
                try {
                    hashData.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                    hashData.append('=');
                    hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                } catch (Exception e) {
                    log.error("Error encoding URL parameter for verification", e);
                }
                
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        String calculatedHash = hmacSHA512(vnpHashSecret, hashData.toString());
        return calculatedHash.equalsIgnoreCase(vnp_SecureHash);
    }

    private String hmacSHA512(String key, String data) {
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
        } catch (Exception ex) {
            log.error("Error generating HMAC SHA512", ex);
            return "";
        }
    }
}
