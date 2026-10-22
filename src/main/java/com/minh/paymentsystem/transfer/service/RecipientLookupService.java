package com.minh.paymentsystem.transfer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.transfer.dto.RecipientLookupRequest;
import com.minh.paymentsystem.transfer.dto.RecipientLookupResponse;
import com.minh.paymentsystem.user.service.UserService;

@Service
@RequiredArgsConstructor
public class RecipientLookupService {
    private final UserService userService;

    @Transactional(readOnly = true)
    public RecipientLookupResponse lookup(RecipientLookupRequest request) {
        return new RecipientLookupResponse(userService.existsByEmail(request.email()));
    }
}
