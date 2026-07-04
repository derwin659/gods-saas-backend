package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.OwnerBranchStatusRequest;
import com.gods.saas.domain.dto.request.OwnerBranchUpsertRequest;
import com.gods.saas.domain.dto.response.OwnerBranchResponse;
import com.gods.saas.security.SecurityUtils;
import com.gods.saas.security.BranchAccessGuard;
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
    private final BranchAccessGuard branchAccessGuard;

    @GetMapping
    public List<OwnerBranchResponse> listBranches(@RequestAttribute("branchId") Long sessionBranchId) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        List<OwnerBranchResponse> branches = ownerBranchService.listBranches(tenantId);
        return branchAccessGuard.isOwner()
                ? branches
                : branches.stream().filter(branch -> branch.branchId().equals(sessionBranchId)).toList();
    }

    @GetMapping("/active")
    public List<OwnerBranchResponse> listActiveBranches(@RequestAttribute("branchId") Long sessionBranchId) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        List<OwnerBranchResponse> branches = ownerBranchService.listActiveBranches(tenantId);
        return branchAccessGuard.isOwner()
                ? branches
                : branches.stream().filter(branch -> branch.branchId().equals(sessionBranchId)).toList();
    }

    @PostMapping
    public OwnerBranchResponse createBranch(@Valid @RequestBody OwnerBranchUpsertRequest request) {
        adminPermissionService.requireOwner();

        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.createBranch(tenantId, request);
    }

    @PutMapping("/{branchId}")
    public OwnerBranchResponse updateBranch(
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long branchId,
            @Valid @RequestBody OwnerBranchUpsertRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return ownerBranchService.updateBranch(tenantId, effectiveBranchId, request);
    }

    @PatchMapping("/{branchId}/status")
    public void updateStatus(
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long branchId,
            @Valid @RequestBody OwnerBranchStatusRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        ownerBranchService.updateStatus(tenantId, effectiveBranchId, request.activo());
    }

    @PostMapping(value = "/{branchId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OwnerBranchResponse uploadImage(
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long branchId,
            @RequestParam("file") MultipartFile file
    ) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return ownerBranchService.uploadImage(tenantId, effectiveBranchId, file);
    }

    @DeleteMapping("/{branchId}/image")
    public OwnerBranchResponse deleteImage(
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long branchId
    ) {
        adminPermissionService.checkPermission("CONFIG_BRANCHES");

        Long tenantId = SecurityUtils.getCurrentTenantId();
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return ownerBranchService.deleteImage(tenantId, effectiveBranchId);
    }
}
