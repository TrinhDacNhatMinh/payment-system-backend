package com.minh.paymentsystem.auth.job;

import com.minh.paymentsystem.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    // Run at 02:00:00 AM every day
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupTokens() {
        log.info("Starting cleanup of Refresh Tokens expired for more than 7 days...");
        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);
        refreshTokenRepository.deleteExpiredTokensOlderThan(threshold);
        log.info("Cleanup of Refresh Tokens completed!");
    }
}
