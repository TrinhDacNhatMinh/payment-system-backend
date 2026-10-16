package com.minh.paymentsystem.user.service;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.entity.UserStatus;
import com.minh.paymentsystem.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

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

    @Nested
    @DisplayName("lockUser()")
    class LockUserTests {

        @Test
        void lockUser_userNotFound_throwsBusinessException() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> userService.lockUser(1L));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
            verify(userRepository, never()).save(any());
        }

        @Test
        void lockUser_validUser_updatesStatusToLocked() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            // when
            userService.lockUser(1L);

            // then
            verify(userRepository, times(1)).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.LOCKED);
        }
    }

    @Nested
    @DisplayName("unlockUser()")
    class UnlockUserTests {

        @Test
        void unlockUser_userNotFound_throwsBusinessException() {
            // given
            when(userRepository.findById(1L)).thenReturn(Optional.empty());

            // when & then
            BusinessException ex = assertThrows(BusinessException.class, () -> userService.unlockUser(1L));
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
            verify(userRepository, never()).save(any());
        }

        @Test
        void unlockUser_validUser_updatesStatusToActive() {
            // given
            testUser.setStatus(UserStatus.LOCKED);
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

            // when
            userService.unlockUser(1L);

            // then
            verify(userRepository, times(1)).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }
}
