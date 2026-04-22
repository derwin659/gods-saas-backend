package com.gods.saas.web.controller;

import com.gods.saas.service.impl.MarketingCampaignProcessorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/marketing-campaigns")
public class MarketingCampaignAdminController {

    private final MarketingCampaignProcessorService marketingCampaignProcessorService;

    @PostMapping("/run")
    public Map<String, String> runNow(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        Long tenantId = Long.valueOf(details.get("tenantId").toString());

        marketingCampaignProcessorService.processTenantCampaigns(tenantId);

        return Map.of("message", "Campañas automáticas procesadas correctamente");
    }
}