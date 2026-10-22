package com.minh.paymentsystem.payment.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class PaymentReturnUrl {

    private static final int MAX_CALLBACK_LENGTH = 16384;

    private final URI target;

    public PaymentReturnUrl(@Value("${vnpay.frontend-result-url:http://localhost:5173/payment/result}") String value,
            Environment environment) {
        URI uri = URI.create(value);
        boolean localHttp = "http".equals(uri.getScheme())
                && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost() == null ? "" : uri.getHost())
                && environment.acceptsProfiles(Profiles.of("dev", "test"));
        if ((!"https".equals(uri.getScheme()) && !localHttp) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !"/payment/result".equals(uri.getPath())) {
            throw new IllegalArgumentException("Configure a trusted frontend payment result URL; HTTP loopback is allowed only in dev/test");
        }
        target = uri;
    }

    public URI forOrder(String orderId) {
        if (!isOrderId(orderId)) {
            return invalid();
        }
        return URI.create(target.toASCIIString() + "?orderId=" + orderId);
    }

    public URI invalid() {
        return URI.create(target.toASCIIString() + "?verification=invalid");
    }

    public URI forVerifiedReturn(String orderId, Map<String, String> parameters) {
        if (!isOrderId(orderId) || !orderId.equals(parameters.get("vnp_TxnRef"))) {
            return invalid();
        }
        String query = parameters.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        if (query.length() > MAX_CALLBACK_LENGTH) {
            return unavailable();
        }
        // A fragment is not sent to the frontend server or included in HTTP referrers.
        return URI.create(forOrder(orderId).toASCIIString() + "#vnpayReturn=" + encode(query));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public URI unavailable() {
        return URI.create(target.toASCIIString() + "?verification=unavailable");
    }

    public static boolean isOrderId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,100}");
    }
}
