package com.gods.saas.web.controller;


import com.gods.saas.domain.dto.response.RewardRedemptionDetailResponse;
import com.gods.saas.domain.dto.response.UseRewardRedemptionResponse;
import com.gods.saas.service.impl.impl.RewardRedemptionAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/rewards/redemptions")
@RequiredArgsConstructor
public class RewardRedemptionAdminController {

    private final RewardRedemptionAdminService rewardRedemptionAdminService;

    @GetMapping("/code/{codigo}")
    public ResponseEntity<?> findByCode(
            @PathVariable String codigo,
            Authentication authentication
    ) {
        try {
            System.out.println("llego un mensaje derwin g");
            RewardRedemptionDetailResponse response =
                    rewardRedemptionAdminService.findByCode(codigo, authentication);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            System.out.println("llego un mensaje derwin");
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/{redemptionId}/use")
    public ResponseEntity<?> useRedemption(
            @PathVariable Long redemptionId,
            Authentication authentication
    ) {
        try {
            UseRewardRedemptionResponse response =
                    rewardRedemptionAdminService.useRedemption(redemptionId, authentication);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
