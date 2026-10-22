package com.minh.paymentsystem.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.minh.paymentsystem.transfer.dto.RecipientLookupRequest;
import com.minh.paymentsystem.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class RecipientLookupServiceTest {
    @Mock
    private UserService userService;
    @InjectMocks
    private RecipientLookupService service;

    @Test
    void shouldTrimEmailAndPreserveCaseForReadOnlyLookup() {
        when(userService.existsByEmail("Recipient@example.com")).thenReturn(true);
        assertThat(service.lookup(new RecipientLookupRequest(" Recipient@example.com ")).exists()).isTrue();
        verify(userService).existsByEmail("Recipient@example.com");
        verifyNoMoreInteractions(userService);
    }

    @Test
    void shouldReturnFalseWhenNoAccountExists() {
        when(userService.existsByEmail("missing@example.com")).thenReturn(false);
        assertThat(service.lookup(new RecipientLookupRequest("missing@example.com")).exists()).isFalse();
        verify(userService).existsByEmail("missing@example.com");
        verifyNoMoreInteractions(userService);
    }
}
