package com.minh.paymentsystem.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.minh.paymentsystem.user.dto.RefreshTokenState;
import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.User;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Query("SELECT new com.minh.paymentsystem.user.dto.RefreshTokenState(r.user.id, r.expiresAt, r.revoked) FROM RefreshToken r WHERE r.token = :token")
    Optional<RefreshTokenState> findStateByToken(@Param("token") String token);

    List<RefreshToken> findAllByUserAndRevokedFalse(User user);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :threshold")
    void deleteExpiredTokensOlderThan(@Param("threshold") Instant threshold);
}
