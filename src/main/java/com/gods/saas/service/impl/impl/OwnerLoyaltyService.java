package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.dto.request.ManualPointsAdjustmentRequest;
import com.gods.saas.domain.dto.response.ManualPointsAdjustmentResponse;
import com.gods.saas.domain.dto.response.OwnerCustomerLoyaltyResponse;

public interface OwnerLoyaltyService {

    OwnerCustomerLoyaltyResponse findCustomerByPhone(Long tenantId, String phone);

    ManualPointsAdjustmentResponse adjustPointsManually(
            Long tenantId,
            Long performedByUserId,
            ManualPointsAdjustmentRequest request
    );
}
