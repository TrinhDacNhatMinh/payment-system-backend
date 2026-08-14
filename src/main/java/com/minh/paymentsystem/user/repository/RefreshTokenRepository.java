package com.minh.paymentsystem.user.repository;

import com.minh.paymentsystem.user.entity.RefreshToken;
import com.minh.paymentsystem.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findAllByUserAndRevokedFalse(User user);
}
