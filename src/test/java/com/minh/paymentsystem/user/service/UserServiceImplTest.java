package com.minh.paymentsystem.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Optional;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .email("test@example.com")
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    void shouldAcquireSessionLockAndRefreshUserById() {
        when(userRepository.findByIdForSessionUpdate(1L)).thenReturn(Optional.of(testUser));

        assertThat(userService.getUserByIdForSessionUpdate(1L)).isSameAs(testUser);

        var order = inOrder(userRepository, entityManager);
        order.verify(userRepository).findByIdForSessionUpdate(1L);
        order.verify(entityManager).refresh(testUser);
    }

    @Test
    void shouldAcquireSessionLockAndRefreshUserByEmail() {
        when(userRepository.findByEmailForSessionUpdate(testUser.getEmail())).thenReturn(Optional.of(testUser));

        assertThat(userService.getUserByEmailForSessionUpdate(testUser.getEmail())).isSameAs(testUser);

        var order = inOrder(userRepository, entityManager);
        order.verify(userRepository).findByEmailForSessionUpdate(testUser.getEmail());
        order.verify(entityManager).refresh(testUser);
    }

    @Test
    void shouldRejectMissingUserWhenAcquiringSessionLock() {
        when(userRepository.findByIdForSessionUpdate(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> userService.getUserByIdForSessionUpdate(1L));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(entityManager);
    }

}
