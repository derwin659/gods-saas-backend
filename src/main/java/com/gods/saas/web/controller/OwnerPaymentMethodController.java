package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.OwnerPaymentMethodResponse;
import com.gods.saas.domain.model.TenantPaymentMethod;
import com.gods.saas.domain.repository.TenantPaymentMethodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/payment-methods")
@RequiredArgsConstructor
public class OwnerPaymentMethodController {

    private final TenantPaymentMethodRepository tenantPaymentMethodRepository;

    @GetMapping
    public List<OwnerPaymentMethodResponse> list(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestParam(required = false) Long branchId
    ) {
        List<TenantPaymentMethod> configuredMethods = loadConfiguredMethods(tenantId, branchId);

        if (configuredMethods == null || configuredMethods.isEmpty()) {
            return defaultPeruFallback();
        }

        Map<String, OwnerPaymentMethodResponse> result = new LinkedHashMap<>();

        for (TenantPaymentMethod method : configuredMethods) {
            if (method == null) continue;

            String code = normalizeCode(method.getCode());
            if (code == null || code.isBlank()) continue;

            String displayName = clean(method.getDisplayName());
            if (displayName == null || displayName.isBlank()) {
                displayName = code;
            }

            result.putIfAbsent(
                    code,
                    OwnerPaymentMethodResponse.builder()
                            .id(method.getId())
                            .code(code)
                            .displayName(displayName)
                            .countryCode(clean(method.getCountryCode()))
                            .active(Boolean.TRUE.equals(method.getActive()))
                            .sortOrder(method.getSortOrder() == null ? 0 : method.getSortOrder())
                            .build()
            );
        }

        if (result.isEmpty()) {
            return defaultPeruFallback();
        }

        return new ArrayList<>(result.values());
    }

    private List<TenantPaymentMethod> loadConfiguredMethods(Long tenantId, Long branchId) {
        if (branchId != null) {
            List<TenantPaymentMethod> branchMethods =
                    tenantPaymentMethodRepository
                            .findByTenant_IdAndBranch_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(
                                    tenantId,
                                    branchId
                            );

            if (branchMethods != null && !branchMethods.isEmpty()) {
                return branchMethods;
            }
        }

        return tenantPaymentMethodRepository
                .findByTenant_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId);
    }

    private List<OwnerPaymentMethodResponse> defaultPeruFallback() {
        return List.of(
                base("CASH", "Efectivo", 1),
                base("CARD", "Tarjeta", 2),
                base("TRANSFER", "Transferencia", 3),
                base("YAPE", "Yape", 4),
                base("PLIN", "Plin", 5)
        );
    }

    private OwnerPaymentMethodResponse base(String code, String displayName, int sortOrder) {
        return OwnerPaymentMethodResponse.builder()
                .id(null)
                .code(code)
                .displayName(displayName)
                .countryCode("PE")
                .active(true)
                .sortOrder(sortOrder)
                .build();
    }

    private String normalizeCode(String value) {
        if (value == null) return null;

        return value
                .trim()
                .toUpperCase()
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace(" ", "_");
    }

    private String clean(String value) {
        if (value == null) return null;
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}