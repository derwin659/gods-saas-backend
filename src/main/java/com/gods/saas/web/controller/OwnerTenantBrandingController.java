package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.OwnerTenantBrandingResponse;
import com.gods.saas.domain.model.Tenant;
import com.gods.saas.domain.repository.TenantRepository;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.CloudinaryStorageService;
import com.gods.saas.utils.SecurityTenantUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/owner/tenant/branding")
@RequiredArgsConstructor
public class OwnerTenantBrandingController {

    private final TenantRepository tenantRepository;
    private final SecurityTenantUtil securityTenantUtil;
    private final AdminPermissionService adminPermissionService;
    private final CloudinaryStorageService cloudinaryStorageService;

    @GetMapping
    public ResponseEntity<OwnerTenantBrandingResponse> getBranding() {
        adminPermissionService.checkPermission("CONFIG_ACCESS");

        Long tenantId = securityTenantUtil.getCurrentTenantId();
        Tenant tenant = findTenant(tenantId);

        return ResponseEntity.ok(map(tenant));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OwnerTenantBrandingResponse> uploadLogo(
            @RequestParam("file") MultipartFile file
    ) {
        adminPermissionService.checkPermission("CONFIG_ACCESS");

        Long tenantId = securityTenantUtil.getCurrentTenantId();
        Tenant tenant = findTenant(tenantId);

        CloudinaryStorageService.UploadResult upload =
                cloudinaryStorageService.uploadTenantLogo(tenantId, file);

        tenant.setLogoUrl(upload.getSecureUrl());
        tenant.setFechaActualizacion(LocalDateTime.now());
        Tenant saved = tenantRepository.save(tenant);

        return ResponseEntity.ok(map(saved));
    }

    private Tenant findTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant no encontrado"));
    }

    private OwnerTenantBrandingResponse map(Tenant tenant) {
        return OwnerTenantBrandingResponse.builder()
                .tenantId(tenant.getId())
                .nombre(tenant.getNombre())
                .logoUrl(tenant.getLogoUrl())
                .ciudad(tenant.getCiudad())
                .businessType(tenant.getBusinessType())
                .build();
    }
}
