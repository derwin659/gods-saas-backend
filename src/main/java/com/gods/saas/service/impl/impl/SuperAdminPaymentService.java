package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.ApprovesPaymentRequest;
import com.gods.saas.domain.dto.request.RejectPaymentRequest;
import com.gods.saas.domain.dto.response.SuperAdminPaymentResponse;
import org.springframework.stereotype.Repository;

import java.util.List;


public interface SuperAdminPaymentService {
    List<SuperAdminPaymentResponse> findPending();
    List<SuperAdminPaymentResponse> findAll();
    void approve(Long paymentId, ApprovesPaymentRequest request);
    void reject(Long paymentId, RejectPaymentRequest request);
}