package com.minh.paymentsystem.common.exception;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.minh.paymentsystem.common.response.BaseResponse;
import com.minh.paymentsystem.transfer.dto.TransferRequestError;
import com.minh.paymentsystem.transfer.exception.TransferRejectedException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<BaseResponse<Void>> handleNotFound(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(BaseResponse.error("NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(TransferRejectedException.class)
    public ResponseEntity<BaseResponse<Void>> handleTransferRejection(TransferRejectedException exception) {
        TransferRequestError error = exception.getError();
        log.warn("Transfer rejected: code={}", error.code());
        return ResponseEntity.status(error.httpStatus())
                .body(BaseResponse.error(error.code(), error.message()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        log.warn("Malformed request body: code={}", ErrorCode.VALIDATION_ERROR);
        return ResponseEntity.badRequest()
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("Business error: code={}", ex.getErrorCode().name());
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(BaseResponse.error(ex.getErrorCode().name(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: code={}, errorCount={}", ErrorCode.VALIDATION_ERROR, ex.getBindingResult().getErrorCount());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: code={}", ErrorCode.ACCESS_DENIED);
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(BaseResponse.error(ErrorCode.ACCESS_DENIED.name(), ErrorCode.ACCESS_DENIED.getMessage()));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<BaseResponse<Void>> handleMethodValidationException(HandlerMethodValidationException ex) {
        if (ex.isForReturnValue()) {
            return handleException(ex);
        }
        log.warn("Request parameter validation failed: code={}", ErrorCode.VALIDATION_ERROR);
        return ResponseEntity.badRequest()
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("Invalid request parameter type: code={}", ErrorCode.VALIDATION_ERROR);
        return ResponseEntity.badRequest()
                .body(BaseResponse.error(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<BaseResponse<Void>> handleOptimisticLockingFailureException(org.springframework.dao.OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification detected: code={}", ErrorCode.CONCURRENT_MODIFICATION);
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(BaseResponse.error(ErrorCode.CONCURRENT_MODIFICATION.name(), ErrorCode.CONCURRENT_MODIFICATION.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(Exception ex) {
        log.error("Internal server error: code={}", ErrorCode.INTERNAL_SERVER_ERROR, ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.name(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
