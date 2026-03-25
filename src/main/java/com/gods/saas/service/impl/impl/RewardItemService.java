package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.RewardItemRequest;
import com.gods.saas.domain.dto.response.RewardItemResponse;

import java.util.List;

public interface RewardItemService {
    List<RewardItemResponse> getAll(Long tenantId, Boolean onlyActive);
    RewardItemResponse create(Long tenantId, RewardItemRequest request);
    RewardItemResponse update(Long tenantId, Long id, RewardItemRequest request);
    void delete(Long tenantId, Long id);

}