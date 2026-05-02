package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateSaleFromAppointmentRequest;
import com.gods.saas.domain.dto.response.CreateSaleFromAppointmentResponse;
import com.gods.saas.domain.dto.response.ProductResponse;
import com.gods.saas.domain.dto.response.SimpleServiceResponse;
import com.gods.saas.service.impl.BarberSaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/barber")
@RequiredArgsConstructor
public class BarberSaleController {

    private final BarberSaleService barberSaleService;

    @PostMapping("/sales/from-appointment")
    public CreateSaleFromAppointmentResponse createSaleFromAppointment(
            Authentication authentication,
            @RequestBody CreateSaleFromAppointmentRequest request
    ) {
        Map<String, Object> claims = getClaims(authentication);

        Long tenantId = toLong(claims.get("tenantId"));
        Long branchId = toLong(claims.get("branchId"));
        Long userId = toLong(claims.get("userId"));

        if (tenantId == null) {
            throw new RuntimeException("El token no contiene tenantId");
        }
        if (branchId == null) {
            throw new RuntimeException("El token no contiene branchId");
        }
        if (userId == null) {
            throw new RuntimeException("El token no contiene userId");
        }

        return barberSaleService.createSaleFromAppointment(
                tenantId,
                branchId,
                userId,
                request
        );
    }



    @GetMapping("/catalog/services")
    public List<SimpleServiceResponse> getServices(Authentication authentication) {
        Map<String, Object> claims = getClaims(authentication);

        Long tenantId = toLong(claims.get("tenantId"));
        if (tenantId == null) {
            throw new RuntimeException("El token no contiene tenantId");
        }

        return barberSaleService.getAvailableServices(tenantId);
    }

    @GetMapping("/catalog/products")
    public List<ProductResponse> getProducts(Authentication authentication) {
        Map<String, Object> claims = getClaims(authentication);

        Long tenantId = toLong(claims.get("tenantId"));
        if (tenantId == null) {
            throw new RuntimeException("El token no contiene tenantId");
        }

        return barberSaleService.getAvailableProducts(tenantId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getClaims(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("No hay sesión autenticada");
        }

        Object details = authentication.getDetails();

        if (details == null) {
            throw new RuntimeException("Authentication.details es null");
        }

        if (details instanceof Map<?, ?> detailsMap) {
            return (Map<String, Object>) detailsMap;
        }

        throw new RuntimeException("No se pudieron leer los claims del token");
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}