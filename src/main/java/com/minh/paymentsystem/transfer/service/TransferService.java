package com.minh.paymentsystem.transfer.service;

import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferResponse;

public interface TransferService {
    TransferResponse transfer(Long fromUserId, TransferRequest request);
}
