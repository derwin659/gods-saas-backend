package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.UpdateWhatsappSettingsRequest;
import com.gods.saas.domain.dto.response.WhatsappSettingsResponse;
import com.gods.saas.service.impl.OwnerWhatsappSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/whatsapp-settings")
@RequiredArgsConstructor
public class OwnerWhatsappSettingsController {

    private final OwnerWhatsappSettingsService service;

    @GetMapping
    public WhatsappSettingsResponse getSettings(HttpServletRequest request) {
        return service.getSettings(resolveTenantId(request));
    }

    @PutMapping
    public WhatsappSettingsResponse updateSettings(
            HttpServletRequest request,
            @RequestBody UpdateWhatsappSettingsRequest body
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
