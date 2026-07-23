package com.gods.saas.web.controller;

import com.gods.saas.security.BranchAccessGuard;

import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
import com.gods.saas.domain.dto.request.RejectSalePaymentRequest;
import com.gods.saas.domain.dto.request.UpdateSaleRequest;
import com.gods.saas.domain.dto.request.PrinterEventRequest;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.service.impl.impl.CashSaleService;
import com.gods.saas.service.impl.AdminPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/owner/cash-sales")
@RequiredArgsConstructor
public class CashSaleController {

    private final CashSaleService cashSaleService;
    private final BranchAccessGuard branchAccessGuard;
    private final AdminPermissionService adminPermissionService;

    @PostMapping
    public SaleResponse create(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CreateCashSaleRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashSaleService.createCashSale(tenantId, effectiveBranchId, userId, request);
    }

    @GetMapping("/today")
    public List<SaleResponse> today(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashSaleService.getTodaySales(tenantId, effectiveBranchId);
    }

    @GetMapping("/pending-validation")
    public List<SaleResponse> pendingValidation(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashSaleService.getPendingValidationSales(tenantId, effectiveBranchId);
    }

    @PostMapping("/{saleId}/approve-payment")
    public SaleResponse approvePayment(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long saleId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String auditReason
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashSaleService.approveSalePayment(tenantId, effectiveBranchId, userId, saleId);
    }

    @PostMapping("/{saleId}/reject-payment")
    public SaleResponse rejectPayment(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long saleId,
            @RequestParam(required = false) Long branchId,
            @RequestBody(required = false) RejectSalePaymentRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        String reason = request != null ? request.getReason() : null;
        return cashSaleService.rejectSalePayment(tenantId, effectiveBranchId, userId, saleId, reason);
    }


    @GetMapping
    public List<SaleResponse> byRange(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashSaleService.getSalesByRange(tenantId, effectiveBranchId, from, to);
    }


    @GetMapping("/by-cash-register/{cashRegisterId}")
    public List<SaleResponse> byCashRegister(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashSaleService.getSalesByCashRegister(tenantId, effectiveBranchId, cashRegisterId);
    }

    @GetMapping("/{saleId}")
    public SaleResponse detail(
            @RequestAttribute("tenantId") Long tenantId,
            @PathVariable Long saleId
    ) {
        return cashSaleService.getById(tenantId, saleId);
    }

    @PutMapping("/{saleId}")
    public SaleResponse update(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long saleId,
            @RequestParam(required = false) Long branchId,
            @RequestBody UpdateSaleRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return cashSaleService.updateSale(tenantId, effectiveBranchId, userId, saleId, request);
    }

    @PostMapping("/{saleId}/print-event")
    public void registerPrintEvent(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long saleId,
            @RequestParam(required = false) Long branchId,
            @RequestBody PrinterEventRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        cashSaleService.registerPrinterEvent(tenantId, effectiveBranchId, userId, saleId, request);
    }

    @PostMapping("/{saleId}/drawer-event")
    public void registerDrawerEvent(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long saleId,
            @RequestParam(required = false) Long branchId,
            @RequestBody PrinterEventRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        cashSaleService.registerDrawerEvent(tenantId, effectiveBranchId, userId, saleId, request);
    }

    @DeleteMapping("/{saleId}")
    public void delete(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long saleId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) String auditReason
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        adminPermissionService.checkPermission("CASH_DELETE_SALES");
        cashSaleService.deleteSale(tenantId, effectiveBranchId, userId, saleId, auditReason);
    }
}