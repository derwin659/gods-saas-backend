package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.BarberCreateRequest;
import com.gods.saas.domain.dto.request.BarberStatusRequest;
import com.gods.saas.domain.dto.request.BarberUpdateRequest;
import com.gods.saas.domain.dto.response.BarberResponse;
import com.gods.saas.service.impl.impl.OwnerBarberService;
import com.gods.saas.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owner/barbers")
@RequiredArgsConstructor
public class OwnerBarberController {

    private final OwnerBarberService ownerBarberService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public List<BarberResponse> listBarbers(
            @RequestParam(required = false) Long branchId,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        return ownerBarberService.listBarbers(tenantId, branchId);
    }

    @PostMapping
    public BarberResponse createBarber(
            @Valid @RequestBody BarberCreateRequest requestBody,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        return ownerBarberService.createBarber(tenantId, requestBody);
    }

    @PutMapping("/{barberId}")
    public BarberResponse updateBarber(
            @PathVariable Long barberId,
            @Valid @RequestBody BarberUpdateRequest requestBody,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        return ownerBarberService.updateBarber(tenantId, barberId, requestBody);
    }

    @PatchMapping("/{barberId}/status")
    public BarberResponse updateStatus(
            @PathVariable Long barberId,
            @Valid @RequestBody BarberStatusRequest requestBody,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        return ownerBarberService.updateStatus(tenantId, barberId, requestBody);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT no enviado o inválido.");
        }
        return authHeader.substring(7);
    }
}
