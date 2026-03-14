package com.gods.saas.service.impl.impl;

import com.gods.saas.domain.dto.response.ClientPromotionResponse;

import java.util.List;

public interface PromotionService {
    List<ClientPromotionResponse> getClientPromotions(String phone);
}
