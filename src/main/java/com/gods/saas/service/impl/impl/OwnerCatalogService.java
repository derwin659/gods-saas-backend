package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.response.SimpleBarberResponse;
import com.gods.saas.domain.dto.response.SimpleCustomerResponse;
import com.gods.saas.domain.dto.response.SimpleServiceResponse;

import java.util.List;

public interface OwnerCatalogService {
    List<SimpleBarberResponse> getBarbers(Long tenantId, Long branchId);
    List<SimpleServiceResponse> getServices(Long tenantId);
    List<SimpleCustomerResponse> searchCustomers(Long tenantId, String query);
}
