package com.gods.saas.controller.owner;

import com.gods.saas.domain.dto.request.OwnerBranchStatusRequest;
import com.gods.saas.domain.dto.request.OwnerBranchUpsertRequest;
import com.gods.saas.domain.dto.response.OwnerBranchResponse;
import com.gods.saas.security.SecurityUtils;
import com.gods.saas.service.impl.OwnerBranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owner/branches")
@RequiredArgsConstructor
public class OwnerBranchController {

    private final OwnerBranchService ownerBranchService;

    @GetMapping
    public List<OwnerBranchResponse> listBranches() {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.listBranches(tenantId);
    }

    @GetMapping("/active")
    public List<OwnerBranchResponse> listActiveBranches() {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.listActiveBranches(tenantId);
    }

    @PostMapping
    public OwnerBranchResponse createBranch(@Valid @RequestBody OwnerBranchUpsertRequest request) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.createBranch(tenantId, request);
    }

    @PutMapping("/{branchId}")
    public OwnerBranchResponse updateBranch(
            @PathVariable Long branchId,
            @Valid @RequestBody OwnerBranchUpsertRequest request
    ) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        return ownerBranchService.updateBranch(tenantId, branchId, request);
    }

    @PatchMapping("/{branchId}/status")
    public void updateStatus(
            @PathVariable Long branchId,
            @Valid @RequestBody OwnerBranchStatusRequest request
    ) {
        Long tenantId = SecurityUtils.getCurrentTenantId();
        ownerBranchService.updateStatus(tenantId, branchId, request.activo());
    }
}