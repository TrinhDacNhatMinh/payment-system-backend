package com.minh.paymentsystem.transfer.service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Validator;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferRequestResponse;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.transfer.exception.TransferRejectedException;
import com.minh.paymentsystem.transfer.security.IdempotencyKey;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final TransferRegistrationService registrationService;
    private final TransferProcessingService processingService;
    private final Validator validator;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TransferResponse transfer(Long userId, List<String> keys, TransferRequest request) {
        UUID key = IdempotencyKey.parse(keys);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        registerRequest(userId, key, request);
        TransferRequestResponse response = processingService.process(userId, key, request);
        if (response.state() == TransferRequestState.REJECTED) {
            // The worker's transaction has committed the rejection before it becomes an HTTP error.
            throw new TransferRejectedException(response.error());
        }
        return response.result();
    }

    @Override
    @Transactional(readOnly = true)
    public TransferRequestResponse getRequest(Long userId, String key) {
        return processingService.getRequest(userId, IdempotencyKey.parse(Collections.singletonList(key)));
    }

    private void registerRequest(Long userId, UUID key, TransferRequest request) {
        try {
            registrationService.register(userId, key, request);
        } catch (DataIntegrityViolationException exception) {
            // register() has rolled back; inspect the winner using a fresh transaction.
            if (!registrationService.exists(userId, key)) {
                throw exception;
            }
        }
    }
}
