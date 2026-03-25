package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.CreateQuickCustomerRequest;
import com.gods.saas.domain.dto.response.CustomerSearchResponse;

import java.util.List;

public interface CustomerSearchService {
    List<CustomerSearchResponse> search(Long tenantId, String q);
    CustomerSearchResponse createQuick(Long tenantId, CreateQuickCustomerRequest request);
}