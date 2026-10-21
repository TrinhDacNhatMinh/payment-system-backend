package com.minh.paymentsystem.transfer.service;

import java.util.List;

import com.minh.paymentsystem.transfer.dto.TransferRequest;
import com.minh.paymentsystem.transfer.dto.TransferRequestResponse;
import com.minh.paymentsystem.transfer.dto.TransferResponse;

public interface TransferService {
    TransferResponse transfer(Long fromUserId, List<String> keys, TransferRequest request);

    TransferRequestResponse getRequest(Long userId, String key);
}
