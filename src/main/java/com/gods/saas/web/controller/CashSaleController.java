package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
import com.gods.saas.domain.dto.request.UpdateSaleRequest;
import com.gods.saas.domain.dto.response.SaleResponse;
import com.gods.saas.service.impl.impl.CashSaleService;
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

    @PostMapping
    public SaleResponse create(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CreateCashSaleRequest request
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashSaleService.createCashSale(tenantId, effectiveBranchId, userId, request);
    }

    @GetMapping("/today")
    public List<SaleResponse> today(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashSaleService.getTodaySales(tenantId, effectiveBranchId);
    }

    @GetMapping
    public List<SaleResponse> byRange(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashSaleService.getSalesByRange(tenantId, effectiveBranchId, from, to);
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
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashSaleService.updateSale(tenantId, effectiveBranchId, userId, saleId, request);
    }

    @DeleteMapping("/{saleId}")
    public void delete(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long saleId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        cashSaleService.deleteSale(tenantId, effectiveBranchId, userId, saleId);
    }
}