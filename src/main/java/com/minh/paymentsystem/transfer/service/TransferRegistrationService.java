package com.minh.paymentsystem.transfer.service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.entity.TransferInstruction;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.transfer.repository.TransferInstructionRepository;
import com.minh.paymentsystem.user.entity.User;
import com.minh.paymentsystem.user.service.UserService;

@Service
@RequiredArgsConstructor
public class TransferRegistrationService {

    private final TransferInstructionRepository repository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void register(Long userId, UUID key, TransferRequest request) {
        if (repository.findByUserIdAndOperationAndIdempotencyKey(userId, TransferInstruction.OPERATION, key).isPresent()) {
            return;
        }
        repository.saveAndFlush(TransferInstruction.builder()
                .user(getSender(userId))
                .operation(TransferInstruction.OPERATION)
                .payloadSchema(TransferInstruction.PAYLOAD_SCHEMA)
                .idempotencyKey(key)
                .toEmail(request.toEmail())
                .amount(request.amount())
                .description(request.description())
                .payloadFingerprint(fingerprint(request))
                .state(TransferRequestState.PENDING)
                .build());
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public boolean exists(Long userId, UUID key) {
        return repository.findByUserIdAndOperationAndIdempotencyKey(userId, TransferInstruction.OPERATION, key).isPresent();
    }

    private User getSender(Long userId) {
        try {
            return userService.getUserById(userId);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED, exception);
            }
            throw exception;
        }
    }

    private String fingerprint(TransferRequest request) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(Arrays.asList(
                    TransferInstruction.PAYLOAD_SCHEMA,
                    request.toEmail(),
                    request.amount().stripTrailingZeros().toPlainString(),
                    request.description()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint transfer request", exception);
        }
    }
}
