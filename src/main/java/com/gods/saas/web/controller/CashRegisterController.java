package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CashMovementRequest;
import com.gods.saas.domain.dto.request.CloseCashRegisterRequest;
import com.gods.saas.domain.dto.request.OpenCashRegisterRequest;
import com.gods.saas.domain.dto.response.CashMovementResponse;
import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.service.impl.impl.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/owner/cash-registers")
@RequiredArgsConstructor
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    @PostMapping("/open")
    public CashRegisterResponse open(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long branchId,
            @RequestBody OpenCashRegisterRequest request
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashRegisterService.open(tenantId, effectiveBranchId, userId, request);
    }

    @GetMapping("/current")
    public CashRegisterResponse current(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashRegisterService.getCurrent(tenantId, effectiveBranchId);
    }

    @PostMapping("/{cashRegisterId}/close")
    public CashRegisterResponse close(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CloseCashRegisterRequest request
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashRegisterService.close(tenantId, effectiveBranchId, cashRegisterId, request);
    }

    @GetMapping("/history")
    public List<CashRegisterResponse> history(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashRegisterService.history(tenantId, effectiveBranchId, from, to);
    }

    @GetMapping("/{cashRegisterId}/movements")
    public List<CashMovementResponse> movements(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashRegisterService.getMovements(tenantId, effectiveBranchId, cashRegisterId);
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
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
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
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashRegisterService.updateMovement(tenantId, effectiveBranchId, movementId, userId, request);
    }

    @DeleteMapping("/movements/{movementId}")
    public ResponseEntity<Void> deleteMovement(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long movementId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        cashRegisterService.deleteMovement(tenantId, effectiveBranchId, movementId, userId);
        return ResponseEntity.noContent().build();
    }
}
