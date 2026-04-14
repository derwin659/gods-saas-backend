package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.CreateBarberPaymentRequest;
import com.gods.saas.domain.dto.response.BarberPaymentPreviewResponse;
import com.gods.saas.domain.dto.response.BarberPaymentResponse;

import java.time.LocalDate;
import java.util.List;

public interface BarberPaymentService {

    BarberPaymentPreviewResponse preview(
            Long tenantId,
            Long branchId,
            Long barberUserId,
            LocalDate periodFrom,
            LocalDate periodTo
    );

    BarberPaymentResponse createPayment(
            Long tenantId,
            Long branchId,
            Long cashRegisterId,
            Long actorUserId,
            CreateBarberPaymentRequest request
    );

    List<BarberPaymentResponse> history(
            Long tenantId,
            Long branchId,
            Long barberUserId
    );
}