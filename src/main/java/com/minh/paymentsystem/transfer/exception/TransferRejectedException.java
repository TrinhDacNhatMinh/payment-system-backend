package com.minh.paymentsystem.transfer.exception;

import lombok.Getter;

import com.minh.paymentsystem.transfer.dto.TransferRequestError;

@Getter
public class TransferRejectedException extends RuntimeException {

    private final TransferRequestError error;

    public TransferRejectedException(TransferRequestError error) {
        super(error.message());
        this.error = error;
    }
}
