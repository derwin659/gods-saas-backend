package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.BarberBranchCompensationDto;
import com.gods.saas.domain.dto.request.SaveBarberBranchCompensationRequest;
import com.gods.saas.security.BranchAccessGuard;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.BarberBranchCompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owner/barbers/{barberUserId}/compensation")
@RequiredArgsConstructor
public class BarberBranchCompensationController {
    private final BarberBranchCompensationService compensationService;
    private final AdminPermissionService adminPermissionService;
    private final BranchAccessGuard branchAccessGuard;

    @GetMapping
    public BarberBranchCompensationDto get(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long barberUserId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkPermission("CONFIG_BARBERS");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return compensationService.get(tenantId, effectiveBranchId, barberUserId);
    }

    @PutMapping
    public BarberBranchCompensationDto save(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long barberUserId,
            @RequestParam(required = false) Long branchId,
            @RequestBody SaveBarberBranchCompensationRequest request
    ) {
        adminPermissionService.checkPermission("CONFIG_BARBERS");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return compensationService.save(tenantId, effectiveBranchId, barberUserId, request);
    }
}
