package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.ServiceRequest;
import com.gods.saas.domain.dto.response.ServiceResponse;

import java.util.List;

public interface OwnerServiceCrudService {
    List<ServiceResponse> findAll(Long tenantId, Boolean onlyActive);
    ServiceResponse findById(Long tenantId, Long serviceId);
    ServiceResponse create(Long tenantId, ServiceRequest request);
    ServiceResponse update(Long tenantId, Long serviceId, ServiceRequest request);
    ServiceResponse toggleStatus(Long tenantId, Long serviceId);
}
