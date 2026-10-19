package com.minh.paymentsystem.auth.job;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.user.service.RefreshTokenService;

@Component
@Slf4j
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    // Run at 02:00:00 AM every day
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupTokens() {
        log.debug("Starting cleanup of Refresh Tokens expired for more than 7 days...");
        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);
        refreshTokenService.cleanUpTokens(threshold);
        log.info("Refresh token cleanup processed: threshold={}", threshold);
    }
}
