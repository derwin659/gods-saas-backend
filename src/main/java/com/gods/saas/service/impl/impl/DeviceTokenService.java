package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.RegisterDeviceTokenRequest;
import com.gods.saas.domain.dto.response.DeviceTokenResponse;

public interface DeviceTokenService {

    DeviceTokenResponse registerCustomerToken(Long tenantId, Long customerId, RegisterDeviceTokenRequest request);

    DeviceTokenResponse registerUserToken(Long tenantId, Long userId, RegisterDeviceTokenRequest request);

    void deactivateCustomerToken(Long tenantId, Long customerId, String token);

    void deactivateUserToken(Long tenantId, Long userId, String token);
}