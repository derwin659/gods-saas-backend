package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.BarberCreateRequest;
import com.gods.saas.domain.dto.request.BarberStatusRequest;
import com.gods.saas.domain.dto.request.BarberUpdateRequest;
import com.gods.saas.domain.dto.response.BarberResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.impl.OwnerBarberService;
import com.gods.saas.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/owner/barbers")
@RequiredArgsConstructor
public class OwnerBarberController {

    private final OwnerBarberService ownerBarberService;
    private final JwtUtil jwtUtil;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public List<BarberResponse> listBarbers(
            @RequestParam(required = false) Long branchId,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        adminPermissionService.checkPermission("CONFIG_BARBERS");
        return ownerBarberService.listBarbers(tenantId, branchId);
    }

    @PostMapping
    public BarberResponse createBarber(
            @Valid @RequestBody BarberCreateRequest requestBody,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        adminPermissionService.checkPermission("CONFIG_BARBERS");
        return ownerBarberService.createBarber(tenantId, requestBody);
    }

    @PutMapping("/{barberId}")
    public BarberResponse updateBarber(
            @PathVariable Long barberId,
            @Valid @RequestBody BarberUpdateRequest requestBody,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        adminPermissionService.checkPermission("CONFIG_BARBERS");
        return ownerBarberService.updateBarber(tenantId, barberId, requestBody);
    }

    @PatchMapping("/{barberId}/status")
    public BarberResponse updateStatus(
            @PathVariable Long barberId,
            @Valid @RequestBody BarberStatusRequest requestBody,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        adminPermissionService.checkPermission("CONFIG_BARBERS");
        return ownerBarberService.updateStatus(tenantId, barberId, requestBody);
    }

    @PostMapping(value = "/{barberId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BarberResponse uploadPhoto(
            @PathVariable Long barberId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        adminPermissionService.checkPermission("CONFIG_BARBERS");
        return ownerBarberService.uploadPhoto(tenantId, barberId, file);
    }

    @DeleteMapping("/{barberId}/photo")
    public BarberResponse deletePhoto(
            @PathVariable Long barberId,
            HttpServletRequest request
    ) {
        Long tenantId = jwtUtil.getTenantIdFromToken(extractToken(request));
        return ownerBarberService.deletePhoto(tenantId, barberId);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT no enviado o inválido.");
        }
        return authHeader.substring(7);
    }


}
