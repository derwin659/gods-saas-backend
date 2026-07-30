package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.UpdateRegionalSettingsRequest;
import com.gods.saas.domain.dto.response.RegionalSettingsResponse;
import com.gods.saas.security.SecurityUtils;
import com.gods.saas.service.impl.RegionalSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/regional-settings")
@RequiredArgsConstructor
public class OwnerRegionalSettingsController {

    private final RegionalSettingsService service;

    @GetMapping
    public RegionalSettingsResponse get(HttpServletRequest request) {
        return service.get(resolveTenantId(request), SecurityUtils.getCurrentUserId());
    }

    @PutMapping
    public RegionalSettingsResponse update(
            HttpServletRequest request,
            @RequestBody UpdateRegionalSettingsRequest body
    ) {
        return service.update(resolveTenantId(request), SecurityUtils.getCurrentUserId(), body);
    }

    private Long resolveTenantId(HttpServletRequest request) {
        Object value = request.getAttribute("tenantId");
        if (value == null) value = request.getAttribute("tenant_id");
        if (value == null) throw new RuntimeException("No se pudo resolver tenantId desde la sesion");
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }
}
