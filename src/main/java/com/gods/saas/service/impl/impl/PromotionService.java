package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.request.PromotionRequest;
import com.gods.saas.domain.dto.response.ClientPromotionResponse;
import com.gods.saas.domain.dto.response.PromotionResponse;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PromotionService {
    List<ClientPromotionResponse> getClientPromotions(String phone);

    List<PromotionResponse> getOwnerPromotions(Long tenantId);

    PromotionResponse getOwnerPromotionById(Long tenantId, Long promotionId);

    PromotionResponse createPromotion(Long tenantId, PromotionRequest request);

    PromotionResponse updatePromotion(Long tenantId, Long promotionId, PromotionRequest request);

    PromotionResponse togglePromotion(Long tenantId, Long promotionId);

    PromotionResponse uploadPromotionImage(Long tenantId, Long promotionId, MultipartFile file);

    void deletePromotion(Long tenantId, Long promotionId);
}
