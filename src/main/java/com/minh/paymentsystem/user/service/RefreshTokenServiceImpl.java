package com.minh.paymentsystem.user.service;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.repository.RefreshTokenRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public void saveToken(RefreshToken token) {
        refreshTokenRepository.save(token);
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        activeTokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(activeTokens);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken getToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    @Override
    @Transactional
    public void cleanUpTokens(Instant threshold) {
        log.debug("Cleaning up revoked or expired refresh tokens older than {}", threshold);
        refreshTokenRepository.deleteExpiredTokensOlderThan(threshold);
    }
}
