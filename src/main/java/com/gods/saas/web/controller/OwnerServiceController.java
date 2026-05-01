package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ServiceRequest;
import com.gods.saas.domain.dto.response.ServiceResponse;
import com.gods.saas.service.impl.impl.OwnerServiceCrudService;
import com.gods.saas.utils.SecurityTenantUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/owner/services")
@RequiredArgsConstructor
public class OwnerServiceController {

    private final OwnerServiceCrudService ownerServiceCrudService;
    private final SecurityTenantUtil securityTenantUtil;

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> findAll(
            @RequestParam(required = false) Boolean onlyActive
    ) {
        Long tenantId = securityTenantUtil.getCurrentTenantId();
        return ResponseEntity.ok(ownerServiceCrudService.findAll(tenantId, onlyActive));
    }

    @GetMapping("/{serviceId}")
    public ResponseEntity<ServiceResponse> findById(@PathVariable Long serviceId) {
        Long tenantId = securityTenantUtil.getCurrentTenantId();
        return ResponseEntity.ok(ownerServiceCrudService.findById(tenantId, serviceId));
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest request) {
        Long tenantId = securityTenantUtil.getCurrentTenantId();
        return ResponseEntity.ok(ownerServiceCrudService.create(tenantId, request));
    }

    @PutMapping("/{serviceId}")
    public ResponseEntity<ServiceResponse> update(
            @PathVariable Long serviceId,
            @Valid @RequestBody ServiceRequest request
    ) {
        Long tenantId = securityTenantUtil.getCurrentTenantId();
        return ResponseEntity.ok(ownerServiceCrudService.update(tenantId, serviceId, request));
    }

    @PatchMapping("/{serviceId}/toggle")
    public ResponseEntity<ServiceResponse> toggle(@PathVariable Long serviceId) {
        Long tenantId = securityTenantUtil.getCurrentTenantId();
        return ResponseEntity.ok(ownerServiceCrudService.toggleStatus(tenantId, serviceId));
    }

    @PostMapping(value = "/{serviceId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceResponse> uploadImage(
            @PathVariable Long serviceId,
            @RequestParam("file") MultipartFile file
    ) {
        Long tenantId = securityTenantUtil.getCurrentTenantId();
        return ResponseEntity.ok(ownerServiceCrudService.uploadImage(tenantId, serviceId, file));
    }

    @DeleteMapping("/{serviceId}/image")
    public ResponseEntity<ServiceResponse> deleteImage(@PathVariable Long serviceId) {
        Long tenantId = securityTenantUtil.getCurrentTenantId();
        return ResponseEntity.ok(ownerServiceCrudService.deleteImage(tenantId, serviceId));
    }
}