package com.gods.saas.service.impl.impl;


import com.gods.saas.domain.dto.request.BarberCreateRequest;
import com.gods.saas.domain.dto.request.BarberStatusRequest;
import com.gods.saas.domain.dto.request.BarberUpdateRequest;
import com.gods.saas.domain.dto.response.BarberResponse;

import java.util.List;

public interface OwnerBarberService {

    List<BarberResponse> listBarbers(Long tenantId, Long branchId);

    BarberResponse createBarber(Long tenantId, BarberCreateRequest request);

    BarberResponse updateBarber(Long tenantId, Long barberId, BarberUpdateRequest request);

    BarberResponse updateStatus(Long tenantId, Long barberId, BarberStatusRequest request);
}
