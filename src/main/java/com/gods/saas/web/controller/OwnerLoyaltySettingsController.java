package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.UpdateLoyaltySettingsRequest;
import com.gods.saas.domain.dto.response.LoyaltySettingsResponse;
import com.gods.saas.service.impl.OwnerLoyaltySettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/loyalty-settings")
@RequiredArgsConstructor
public class OwnerLoyaltySettingsController {

    private final OwnerLoyaltySettingsService service;

    @GetMapping
    public LoyaltySettingsResponse getSettings(HttpServletRequest request) {
        return service.getSettings(resolveTenantId(request));
    }

    @PutMapping
    public LoyaltySettingsResponse updateSettings(
            HttpServletRequest request,
            @RequestBody UpdateLoyaltySettingsRequest body
    ) {
        return service.updateSettings(resolveTenantId(request), body);
    }

    private Long resolveTenantId(HttpServletRequest request) {
        Object value = request.getAttribute("tenantId");

        if (value == null) {
            value = request.getAttribute("tenant_id");
        }

        if (value == null) {
            throw new RuntimeException("No se pudo resolver tenantId desde la sesion");
        }

        if (value instanceof Number n) {
            return n.longValue();
        }

        return Long.parseLong(value.toString());
    }
}
