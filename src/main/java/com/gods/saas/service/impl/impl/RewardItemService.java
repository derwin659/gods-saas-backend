package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.RewardItemRequest;
import com.gods.saas.domain.dto.response.RewardItemResponse;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RewardItemService {
    List<RewardItemResponse> getAll(Long tenantId, Boolean onlyActive);
    RewardItemResponse create(Long tenantId, RewardItemRequest request);
    RewardItemResponse update(Long tenantId, Long id, RewardItemRequest request);
    RewardItemResponse uploadRewardImage(Long tenantId, Long id, MultipartFile file);

    void delete(Long tenantId, Long id);

}