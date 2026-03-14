package com.gods.saas.service.impl.impl;




import com.gods.saas.domain.dto.response.RewardRedemptionDetailResponse;
import com.gods.saas.domain.dto.response.UseRewardRedemptionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public interface RewardRedemptionAdminService {
    RewardRedemptionDetailResponse findByCode(String codigo, Authentication authentication);
    UseRewardRedemptionResponse useRedemption(Long redemptionId, Authentication authentication);
}
