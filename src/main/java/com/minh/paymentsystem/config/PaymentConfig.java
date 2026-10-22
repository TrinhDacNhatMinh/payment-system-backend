package com.minh.paymentsystem.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

    @Bean
    public Clock paymentClock() {
        return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
