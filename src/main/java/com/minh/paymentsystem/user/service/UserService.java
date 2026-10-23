package com.minh.paymentsystem.user.service;

import java.util.Optional;

import com.minh.paymentsystem.user.entity.User;

public interface UserService {
    User getUserById(Long userId);

    User getUserByEmail(String email);

    Optional<User> findUserByEmail(String email);

    User getUserByIdForSessionUpdate(Long userId);

    User getUserByEmailForSessionUpdate(String email);

    boolean existsByEmail(String email);

    User createUser(User user);
}
