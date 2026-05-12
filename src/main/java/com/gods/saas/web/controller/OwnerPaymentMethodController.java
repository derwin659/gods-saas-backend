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
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;

        List<TenantPaymentMethod> allMethods = tenantPaymentMethodRepository
                .findByTenant_IdAndActiveTrueOrderBySortOrderAscDisplayNameAsc(tenantId);

        List<TenantPaymentMethod> configured = allMethods.stream()
                .filter(method -> method.getBranch() == null
                        || (effectiveBranchId != null && method.getBranch().getId().equals(effectiveBranchId)))
                .toList();

        Map<String, OwnerPaymentMethodResponse> result = new LinkedHashMap<>();

        addBase(result, "CASH", "Efectivo", 1);
        addBase(result, "CARD", "Tarjeta", 2);
        addBase(result, "TRANSFER", "Transferencia", 3);

        if (configured.isEmpty()) {
            // Compatibilidad para negocios actuales de Perú que ya usan la app instalada.
            addBase(result, "YAPE", "Yape", 4);
            addBase(result, "PLIN", "Plin", 5);
            return new ArrayList<>(result.values());
        }

        // Primero métodos globales del tenant.
        configured.stream()
                .filter(method -> method.getBranch() == null)
                .forEach(method -> putConfigured(result, method));

        // Luego métodos específicos de la sede; si el code coincide, sobreescribe el global.
        configured.stream()
                .filter(method -> method.getBranch() != null)
                .forEach(method -> putConfigured(result, method));

        return new ArrayList<>(result.values());
    }

    private void addBase(
            Map<String, OwnerPaymentMethodResponse> map,
            String code,
            String displayName,
            Integer sortOrder
    ) {
        map.put(code, OwnerPaymentMethodResponse.base(code, displayName, sortOrder));
    }

    private void putConfigured(Map<String, OwnerPaymentMethodResponse> map, TenantPaymentMethod method) {
        if (method == null || method.getCode() == null || method.getCode().isBlank()) {
            return;
        }

        String code = method.getCode().trim().toUpperCase();
        map.put(code, OwnerPaymentMethodResponse.fromEntity(method));
    }
}
