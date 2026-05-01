package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.ServiceRequest;
import com.gods.saas.domain.dto.response.ServiceResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OwnerServiceCrudService {
    List<ServiceResponse> findAll(Long tenantId, Boolean onlyActive);

    ServiceResponse findById(Long tenantId, Long serviceId);

    ServiceResponse create(Long tenantId, ServiceRequest request);

    ServiceResponse update(Long tenantId, Long serviceId, ServiceRequest request);

    ServiceResponse toggleStatus(Long tenantId, Long serviceId);

    ServiceResponse uploadImage(Long tenantId, Long serviceId, MultipartFile file);

    ServiceResponse deleteImage(Long tenantId, Long serviceId);
}