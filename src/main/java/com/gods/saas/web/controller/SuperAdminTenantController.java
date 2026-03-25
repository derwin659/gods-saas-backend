package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ChangePlancRequest;
import com.gods.saas.domain.dto.request.SuperAdminCreateTenantRequest;
import com.gods.saas.domain.dto.response.SuperAdminTenantResponse;
import com.gods.saas.service.impl.impl.SuperAdminTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/super-admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminTenantController {

    private final SuperAdminTenantService service;

    @GetMapping
    public ResponseEntity<List<SuperAdminTenantResponse>> findAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<SuperAdminTenantResponse> findById(@PathVariable Long tenantId) {
        return ResponseEntity.ok(service.findById(tenantId));
    }

    @PostMapping
    public ResponseEntity<SuperAdminTenantResponse> create(@RequestBody SuperAdminCreateTenantRequest request) {
        return ResponseEntity.ok(service.create(request));
    }

    @PutMapping("/{tenantId}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long tenantId) {
        service.activate(tenantId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{tenantId}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable Long tenantId) {
        service.suspend(tenantId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{tenantId}/plan")
    public ResponseEntity<Void> changePlan(@PathVariable Long tenantId,
                                           @RequestBody ChangePlancRequest request) {
        service.changePlan(tenantId, request);
        return ResponseEntity.ok().build();
    }
}