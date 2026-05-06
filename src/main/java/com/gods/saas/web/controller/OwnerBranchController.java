package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.OwnerBranchStatusRequest;
import com.gods.saas.domain.dto.request.OwnerBranchUpsertRequest;
import com.gods.saas.domain.dto.response.OwnerBranchResponse;
import com.gods.saas.security.SecurityUtils;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.OwnerBranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/owner/branches")
@RequiredArgsConstructor
public class OwnerBranchController {

    private final OwnerBranchService ownerBranchService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public List<OwnerBranchResponse> listBranches() {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.listBranches(tenantId);
    }

    @GetMapping("/active")
    public List<OwnerBranchResponse> listActiveBranches() {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.listActiveBranches(tenantId);
    }

    @PostMapping
    public OwnerBranchResponse createBranch(@Valid @RequestBody OwnerBranchUpsertRequest request) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.createBranch(tenantId, request);
    }

    @PutMapping("/{branchId}")
    public OwnerBranchResponse updateBranch(
            @PathVariable Long branchId,
            @Valid @RequestBody OwnerBranchUpsertRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.updateBranch(tenantId, branchId, request);
    }

    @PatchMapping("/{branchId}/status")
    public void updateStatus(
            @PathVariable Long branchId,
            @Valid @RequestBody OwnerBranchStatusRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        ownerBranchService.updateStatus(tenantId, branchId, request.activo());
    }

    @PostMapping(value = "/{branchId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OwnerBranchResponse uploadImage(
            @PathVariable Long branchId,
            @RequestParam("file") MultipartFile file
    ) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.uploadImage(tenantId, branchId, file);
    }

    @DeleteMapping("/{branchId}/image")
    public OwnerBranchResponse deleteImage(@PathVariable Long branchId) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.deleteImage(tenantId, branchId);
    }
}