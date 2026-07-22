package com.gods.saas.web.controller;

import com.gods.saas.security.BranchAccessGuard;

import com.gods.saas.domain.dto.request.CashMovementRequest;
import com.gods.saas.domain.dto.request.CashFundMovementRequest;
import com.gods.saas.domain.dto.request.CloseCashRegisterRequest;
import com.gods.saas.domain.dto.request.OpenCashRegisterRequest;
import com.gods.saas.domain.dto.request.ReconcileCashRegisterRequest;
import com.gods.saas.domain.dto.response.CashMovementResponse;
import com.gods.saas.domain.dto.response.CashFundMovementResponse;
import com.gods.saas.domain.dto.response.CashFundSummaryResponse;
import com.gods.saas.domain.dto.response.CashAuditLogResponse;
import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.service.impl.impl.CashRegisterService;
import com.gods.saas.service.impl.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/owner/cash-registers")
@RequiredArgsConstructor
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;
    private final BranchAccessGuard branchAccessGuard;
    private final AdminPermissionService adminPermissionService;

    @PostMapping("/open")
    public CashRegisterResponse open(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long branchId,
            @RequestBody OpenCashRegisterRequest request
    ) {
        adminPermissionService.checkPermission("CASH_OPEN");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return protectFundData(cashRegisterService.open(tenantId, effectiveBranchId, userId, request));
    }

    @GetMapping("/current")
    public CashRegisterResponse current(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return protectFundData(cashRegisterService.getCurrent(tenantId, effectiveBranchId));
    }

    @GetMapping("/reconciliation-pending")
    public CashRegisterResponse pendingReconciliation(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return protectFundData(cashRegisterService.getPendingReconciliation(tenantId, effectiveBranchId));
    }
    @PostMapping("/{cashRegisterId}/reconcile")
    public CashRegisterResponse reconcile(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId,
            @RequestBody ReconcileCashRegisterRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return protectFundData(cashRegisterService.reconcile(tenantId, effectiveBranchId, cashRegisterId, userId, request));
    }
    @PostMapping("/{cashRegisterId}/close")
    public CashRegisterResponse close(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CloseCashRegisterRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return protectFundData(cashRegisterService.close(tenantId, effectiveBranchId, cashRegisterId, request));
    }

    @GetMapping("/history")
    public List<CashRegisterResponse> history(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        boolean canManageFund = canManageFund();
        return cashRegisterService.history(tenantId, effectiveBranchId, from, to)
                .stream()
                .map(item -> protectFundData(item, canManageFund))
                .toList();
    }

    @GetMapping("/audit")
    public List<CashAuditLogResponse> audit(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long cashRegisterId,

            @RequestParam(required = false) Long actorUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashRegisterService.audit(tenantId, effectiveBranchId, cashRegisterId, actorUserId, from, to);
    }
    @GetMapping("/fund")
    public CashFundSummaryResponse fundSummary(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashRegisterService.getFundSummary(tenantId, effectiveBranchId);
    }

    @GetMapping("/fund/movements")
    public List<CashFundMovementResponse> fundMovements(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashRegisterService.getFundMovements(tenantId, effectiveBranchId);
    }

    @PostMapping("/fund/movements")
    public CashFundMovementResponse createFundMovement(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CashFundMovementRequest request
    ) {
        adminPermissionService.checkOwnerOrAdminPermission("CASH_FUND_MANAGE");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashRegisterService.createFundMovement(tenantId, effectiveBranchId, userId, request);
    }
    @GetMapping("/{cashRegisterId}/movements")
    public List<CashMovementResponse> movements(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return protectFundMovements(cashRegisterService.getMovements(tenantId, effectiveBranchId, cashRegisterId), canManageFund());
    }

    @PostMapping("/{cashRegisterId}/movements")
    public CashMovementResponse createMovement(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CashMovementRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashRegisterService.createMovement(tenantId, effectiveBranchId, cashRegisterId, userId, request);
    }

    @PutMapping("/movements/{movementId}")
    public CashMovementResponse updateMovement(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long movementId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CashMovementRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashRegisterService.updateMovement(tenantId, effectiveBranchId, movementId, userId, request);
    }

    @DeleteMapping("/movements/{movementId}")
    public ResponseEntity<Void> deleteMovement(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long movementId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String auditReason
    ) {
        adminPermissionService.checkPermission("CASH_DELETE_MOVEMENTS");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        cashRegisterService.deleteMovement(tenantId, effectiveBranchId, movementId, userId, auditReason);
        return ResponseEntity.noContent().build();
    }
    private boolean canManageFund() {
        return adminPermissionService.hasCurrentOwnerOrAdminPermission("CASH_FUND_MANAGE");
    }

    private CashRegisterResponse protectFundData(CashRegisterResponse response) {
        return protectFundData(response, canManageFund());
    }

    private CashRegisterResponse protectFundData(CashRegisterResponse response, boolean canManageFund) {
        if (response == null || canManageFund) return response;
        response.setAccumulatedFundBalance(BigDecimal.ZERO);
        response.setAccumulatedFundBalances(List.of());
        response.setMovements(protectFundMovements(response.getMovements(), false));
        return response;
    }

    private List<CashMovementResponse> protectFundMovements(
            List<CashMovementResponse> movements,
            boolean canManageFund
    ) {
        if (movements == null) return List.of();
        if (canManageFund) return movements;
        return movements.stream()
                .filter(item -> !"ACCUMULATED_FUND".equalsIgnoreCase(item.getFundingSource()))
                .toList();
    }
}
