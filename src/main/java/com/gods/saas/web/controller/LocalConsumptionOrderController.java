package com.gods.saas.web.controller;

import com.gods.saas.security.BranchAccessGuard;

import com.gods.saas.domain.dto.request.UpdateLocalConsumptionOrderRequest;
import com.gods.saas.domain.dto.response.LocalConsumptionOrderResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.LocalConsumptionOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api/owner/local-consumption-orders")
public class LocalConsumptionOrderController {
    private final LocalConsumptionOrderService localConsumptionOrderService;
    private final AdminPermissionService adminPermissionService;
    private final BranchAccessGuard branchAccessGuard;

    @GetMapping
    public List<LocalConsumptionOrderResponse> ownerOrders(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String status
    ) {
        adminPermissionService.checkPermission("CASH_REGISTER");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return localConsumptionOrderService.ownerOrders(tenantId, effectiveBranchId, status);
    }

    @PostMapping("/{orderId}/reject")
    public LocalConsumptionOrderResponse reject(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long orderId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) UpdateLocalConsumptionOrderRequest request
    ) {
        adminPermissionService.checkPermission("CASH_REGISTER");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return localConsumptionOrderService.reject(
                tenantId,
                effectiveBranchId,
                orderId,
                request != null ? request.getNote() : null
        );
    }

    @PostMapping("/{orderId}/complete")
    public LocalConsumptionOrderResponse complete(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long orderId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) UpdateLocalConsumptionOrderRequest request
    ) {
        adminPermissionService.checkPermission("CASH_REGISTER");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return localConsumptionOrderService.complete(
                tenantId,
                effectiveBranchId,
                userId,
                orderId,
                request != null ? request.getSaleId() : null,
                request != null ? request.getPaymentMethod() : null
        );
    }
}
