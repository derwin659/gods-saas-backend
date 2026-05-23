package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.ClientRewardRedemptionResponse;
import com.gods.saas.domain.dto.response.RedeemRewardResponse;
import com.gods.saas.service.impl.ClientRewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
public class ClientRewardController {

    private final ClientRewardService clientRewardService;

    @GetMapping("/rewards/redemptions")
    public List<ClientRewardRedemptionResponse> listRedemptions(Authentication authentication) {
        return clientRewardService.listRedemptions(authentication);
    }

    @PostMapping("/rewards/{rewardId}/redeem")
    public RedeemRewardResponse redeemReward(
            Authentication authentication,
            @PathVariable Long rewardId
    ) {
        return clientRewardService.redeemReward(authentication, rewardId);
    }
}
