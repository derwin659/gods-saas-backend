package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.SuperAdminDashboardResponse;
import com.gods.saas.service.impl.impl.SuperAdminTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController

@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
public class SuperAdminDashboardController {

    private final SuperAdminTenantService tenantService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/dashboard")
    public ResponseEntity<SuperAdminDashboardResponse> dashboard() {
        return ResponseEntity.ok(tenantService.dashboard());
    }
}