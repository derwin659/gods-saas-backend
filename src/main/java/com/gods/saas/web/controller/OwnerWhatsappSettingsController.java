package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.OwnerWhatsappVerificationConfirmRequest;
import com.gods.saas.domain.dto.request.OwnerWhatsappVerificationRequest;
import com.gods.saas.domain.dto.request.UpdateWhatsappSettingsRequest;
import com.gods.saas.domain.dto.response.OwnerWhatsappVerificationResponse;
import com.gods.saas.domain.dto.response.WhatsappSettingsResponse;
import com.gods.saas.security.SecurityUtils;
import com.gods.saas.service.impl.OwnerWhatsappPhoneVerificationService;
import com.gods.saas.service.impl.OwnerWhatsappSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/whatsapp-settings")
@RequiredArgsConstructor
public class OwnerWhatsappSettingsController {

    private final OwnerWhatsappSettingsService service;
    private final OwnerWhatsappPhoneVerificationService phoneVerificationService;

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

    @GetMapping("/recipient-verification")
    public OwnerWhatsappVerificationResponse getRecipientVerification(HttpServletRequest request) {
        return phoneVerificationService.getStatus(
                resolveTenantId(request),
                SecurityUtils.getCurrentUserId()
        );
    }

    @PostMapping("/recipient-verification/request")
    public OwnerWhatsappVerificationResponse requestRecipientVerification(
            HttpServletRequest request,
            @RequestBody OwnerWhatsappVerificationRequest body
    ) {
        return phoneVerificationService.requestCode(
                resolveTenantId(request),
                SecurityUtils.getCurrentUserId(),
                body == null ? null : body.getPhone()
        );
    }

    @PostMapping("/recipient-verification/verify")
    public OwnerWhatsappVerificationResponse verifyRecipient(
            HttpServletRequest request,
            @RequestBody OwnerWhatsappVerificationConfirmRequest body
    ) {
        return phoneVerificationService.verifyCode(
                resolveTenantId(request),
                SecurityUtils.getCurrentUserId(),
                body == null ? null : body.getCode()
        );
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
