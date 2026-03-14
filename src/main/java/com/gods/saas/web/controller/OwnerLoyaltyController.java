package com.gods.saas.controller;

import com.gods.saas.domain.dto.request.ManualPointsAdjustmentRequest;
import com.gods.saas.domain.dto.response.ManualPointsAdjustmentResponse;
import com.gods.saas.domain.dto.response.OwnerCustomerLoyaltyResponse;
import com.gods.saas.service.impl.impl.OwnerLoyaltyService;
import com.gods.saas.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/owner/loyalty")
@RequiredArgsConstructor
public class OwnerLoyaltyController {

    private final OwnerLoyaltyService ownerLoyaltyService;
    private final JwtUtil jwtUtil;

    @GetMapping("/customer-by-phone")
    public OwnerCustomerLoyaltyResponse findCustomerByPhone(
            @RequestParam String phone,
            HttpServletRequest request
    ) {
        String token = extractToken(request);
        Long tenantId = jwtUtil.getTenantIdFromToken(token);

        return ownerLoyaltyService.findCustomerByPhone(tenantId, phone);
    }

    @PostMapping("/manual-adjustment")
    public ManualPointsAdjustmentResponse adjustPoints(
            @Valid @RequestBody ManualPointsAdjustmentRequest requestBody,
            HttpServletRequest request
    ) {
        String token = extractToken(request);
        Long tenantId = jwtUtil.getTenantIdFromToken(token);
        Long userId = jwtUtil.getUserIdFromToken(token);

        return ownerLoyaltyService.adjustPointsManually(
                tenantId,
                userId,
                requestBody
        );
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT no enviado o inválido.");
        }

        return authHeader.substring(7);
    }
}