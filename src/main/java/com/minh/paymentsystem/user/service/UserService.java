package com.minh.paymentsystem.user.service;

import com.minh.paymentsystem.common.dto.PageResponse;
import com.minh.paymentsystem.user.dto.UserResponse;
import com.minh.paymentsystem.user.entity.User;
import org.springframework.data.domain.Pageable;

public interface UserService {
    void lockUser(Long userId);

    void unlockUser(Long userId);

    PageResponse<UserResponse> getAllUsers(Pageable pageable);

    User getUserById(Long userId);

    User getUserByEmail(String email);

    boolean existsByEmail(String email);

    User createUser(User user);
}
