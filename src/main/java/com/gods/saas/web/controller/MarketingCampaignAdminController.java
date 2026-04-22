package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.MarketingCampaignRequest;
import com.gods.saas.domain.dto.response.MarketingCampaignResponse;
import com.gods.saas.service.impl.MarketingCampaignProcessorService;
import com.gods.saas.service.impl.impl.MarketingCampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/marketing-campaigns")
public class MarketingCampaignAdminController {

    private final MarketingCampaignService marketingCampaignService;
    private final MarketingCampaignProcessorService marketingCampaignProcessorService;

    @GetMapping
    public List<MarketingCampaignResponse> findAll(Authentication authentication) {
        Long tenantId = tenantId(authentication);
        return marketingCampaignService.findAll(tenantId);
    }

    @PostMapping
    public MarketingCampaignResponse create(
            @RequestBody MarketingCampaignRequest request,
            Authentication authentication
    ) {
        Long tenantId = tenantId(authentication);
        return marketingCampaignService.create(tenantId, request);
    }

    @PutMapping("/{id}")
    public MarketingCampaignResponse update(
            @PathVariable Long id,
            @RequestBody MarketingCampaignRequest request,
            Authentication authentication
    ) {
        Long tenantId = tenantId(authentication);
        return marketingCampaignService.update(tenantId, id, request);
    }

    @PostMapping("/{id}/toggle")
    public MarketingCampaignResponse toggle(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long tenantId = tenantId(authentication);
        return marketingCampaignService.toggle(tenantId, id);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Long tenantId = tenantId(authentication);
        marketingCampaignService.delete(tenantId, id);
        return Map.of("message", "Campaña eliminada correctamente");
    }

    @PostMapping("/run")
    public Map<String, String> runNow(Authentication authentication) {
        Long tenantId = tenantId(authentication);
        marketingCampaignProcessorService.processTenantCampaigns(tenantId);
        return Map.of("message", "Campañas ejecutadas correctamente");
    }

    private Long tenantId(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        return Long.valueOf(details.get("tenantId").toString());
    }
}