package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.RedeemRewardResponse;
import com.gods.saas.service.impl.ClientRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientRewardController {

    private final ClientRewardService clientRewardService;

    @PostMapping("/rewards/{rewardId}/redeem")
    public RedeemRewardResponse redeemReward(
            Authentication authentication,
            @PathVariable Long rewardId
    ) {
        return clientRewardService.redeemReward(authentication, rewardId);
    }
}
