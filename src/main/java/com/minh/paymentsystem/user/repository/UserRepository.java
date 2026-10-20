package com.minh.paymentsystem.user.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.minh.paymentsystem.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    String SESSION_LOCK_QUERY_TIMEOUT_MILLIS = "5000";

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = SESSION_LOCK_QUERY_TIMEOUT_MILLIS))
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForSessionUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = SESSION_LOCK_QUERY_TIMEOUT_MILLIS))
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForSessionUpdate(@Param("email") String email);

    boolean existsByEmail(String email);
}
