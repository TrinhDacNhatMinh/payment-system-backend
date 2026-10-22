package com.minh.paymentsystem.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Optional;

import com.minh.paymentsystem.payment.service.PaymentReturnUrl;

public record VnpayNotification(
        String orderId,
        BigDecimal gatewayAmount,
        String responseCode,
        String transactionStatus,
        String transactionNo,
        LocalDateTime paidAt) {

    private static final DateTimeFormatter PAY_DATE = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);

    public static Optional<VnpayNotification> parse(Map<String, String> params) {
        String orderId = params.get("vnp_TxnRef");
        String amount = params.get("vnp_Amount");
        String response = params.get("vnp_ResponseCode");
        String status = params.get("vnp_TransactionStatus");
        String transactionNo = params.get("vnp_TransactionNo");
        if (!PaymentReturnUrl.isOrderId(orderId) || !matches(amount, "[0-9]{1,18}")
                || !matches(response, "[0-9]{2}") || !matches(status, "[0-9]{2}")
                || !matches(transactionNo, "[0-9]{1,15}")) {
            return Optional.empty();
        }
        LocalDateTime paidAt = null;
        String payDate = params.get("vnp_PayDate");
        if (payDate != null && !payDate.isEmpty()) {
            if (!matches(payDate, "[0-9]{14}")) {
                return Optional.empty();
            }
            try {
                paidAt = LocalDateTime.parse(payDate, PAY_DATE);
            } catch (DateTimeParseException exception) {
                return Optional.empty();
            }
        }
        if ("00".equals(response) && "00".equals(status) && (paidAt == null || new BigDecimal(transactionNo).signum() == 0)) {
            return Optional.empty();
        }
        return Optional.of(new VnpayNotification(orderId, new BigDecimal(amount), response, status, transactionNo, paidAt));
    }

    private static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }
}
