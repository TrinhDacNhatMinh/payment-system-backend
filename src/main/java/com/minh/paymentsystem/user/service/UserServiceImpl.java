package com.minh.paymentsystem.user.service;

import java.util.Optional;
import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public User getUserByIdForSessionUpdate(Long userId) {
        User user = userRepository.findByIdForSessionUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // A locking query may return a managed instance loaded before the lock was acquired.
        entityManager.refresh(user);
        return user;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public User getUserByEmailForSessionUpdate(String email) {
        User user = userRepository.findByEmailForSessionUpdate(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        entityManager.refresh(user);
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public User createUser(User user) {
        return userRepository.save(user);
    }
}
