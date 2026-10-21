package com.minh.paymentsystem.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.minh.paymentsystem.common.exception.BusinessException;
import com.minh.paymentsystem.common.exception.ErrorCode;
import com.minh.paymentsystem.transaction.enums.TransactionStatus;
import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferRequestError;
import com.minh.paymentsystem.transfer.dto.TransferRequestResponse;
import com.minh.paymentsystem.transfer.dto.TransferResponse;
import com.minh.paymentsystem.transfer.enums.TransferRequestState;
import com.minh.paymentsystem.transfer.exception.TransferRejectedException;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    private static final UUID KEY = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Mock
    private TransferRegistrationService registrationService;
    @Mock
    private TransferProcessingService processingService;
    @Mock
    private Validator validator;
    @InjectMocks
    private TransferServiceImpl service;

    private TransferRequest request;

    @BeforeEach
    void setUp() {
        request = new TransferRequest("recipient@example.com", new BigDecimal("1000.50"), null);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "bad",
            "550e8400-e29b-11d4-a716-446655440000",
            " 550e8400-e29b-41d4-a716-446655440000",
            "550e8400-e29b-41d4-a716-446655440000,550e8400-e29b-41d4-a716-446655440000"
    })
    void shouldRejectInvalidKeyBeforeRegistration(String key) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.transfer(1L, Arrays.asList(key), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        verifyNoInteractions(registrationService, processingService, validator);
    }

    @Test
    void shouldRejectMissingOrDuplicateHeaders() {
        assertThrows(BusinessException.class, () -> service.transfer(1L, null, request));
        assertThrows(BusinessException.class, () -> service.transfer(1L, List.of(KEY.toString(), KEY.toString()), request));
        verifyNoInteractions(registrationService, processingService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotRegisterInvalidPayload() {
        ConstraintViolation<TransferRequest> violation = mock(ConstraintViolation.class);
        when(validator.validate(request)).thenReturn(Set.of(violation));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.transfer(1L, List.of(KEY.toString()), request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(registrationService, processingService);
    }

    @Test
    void shouldReturnReceiptAfterWorkerCommit() {
        TransferResponse receipt = new TransferResponse(10L, BigDecimal.ZERO, request.amount(), TransactionStatus.SUCCESS, LocalDateTime.now());
        when(processingService.process(1L, KEY, request)).thenReturn(
                new TransferRequestResponse(KEY, TransferRequestState.SUCCEEDED, request, receipt, null));

        assertThat(service.transfer(1L, List.of(KEY.toString().toUpperCase()), request)).isSameAs(receipt);
        var order = inOrder(registrationService, processingService);
        order.verify(registrationService).register(1L, KEY, request);
        order.verify(processingService).process(1L, KEY, request);
    }

    @Test
    void shouldReadWinnerAfterRegistrationTransactionRolledBack() {
        doThrow(new DataIntegrityViolationException("scope conflict")).when(registrationService).register(1L, KEY, request);
        when(registrationService.exists(1L, KEY)).thenReturn(true);
        TransferResponse receipt = new TransferResponse(10L, BigDecimal.ZERO, request.amount(), TransactionStatus.SUCCESS, LocalDateTime.now());
        when(processingService.process(1L, KEY, request)).thenReturn(
                new TransferRequestResponse(KEY, TransferRequestState.SUCCEEDED, request, receipt, null));

        assertThat(service.transfer(1L, List.of(KEY.toString()), request)).isSameAs(receipt);
    }

    @Test
    void shouldPropagateUnrelatedRegistrationConstraintFailure() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("other constraint");
        doThrow(failure).when(registrationService).register(1L, KEY, request);
        when(registrationService.exists(1L, KEY)).thenReturn(false);

        assertThat(assertThrows(DataIntegrityViolationException.class,
                () -> service.transfer(1L, List.of(KEY.toString()), request))).isSameAs(failure);
        verifyNoInteractions(processingService);
    }

    @Test
    void shouldReplayStoredRejectionAfterWorkerReturns() {
        TransferRequestError error = new TransferRequestError(400, "WALLET_INSUFFICIENT_BALANCE", "Stored safe message");
        when(processingService.process(1L, KEY, request)).thenReturn(
                new TransferRequestResponse(KEY, TransferRequestState.REJECTED, request, null, error));

        TransferRejectedException exception = assertThrows(TransferRejectedException.class,
                () -> service.transfer(1L, List.of(KEY.toString()), request));

        assertThat(exception.getError()).isSameAs(error);
    }

    @Test
    void shouldScopeLookupToCurrentUserWithoutRegisteringOrProcessing() {
        TransferRequestResponse pending = new TransferRequestResponse(KEY, TransferRequestState.PENDING, request, null, null);
        when(processingService.getRequest(2L, KEY)).thenReturn(pending);

        assertThat(service.getRequest(2L, KEY.toString())).isSameAs(pending);
        verifyNoInteractions(registrationService);
        verify(processingService, never()).process(any(), any(), any());
    }
}
