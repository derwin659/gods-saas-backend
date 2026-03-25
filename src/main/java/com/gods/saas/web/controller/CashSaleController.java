package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateCashSaleRequest;
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
            @RequestAttribute("branchId") Long branchId,
            @RequestAttribute("userId") Long userId,
            @RequestBody CreateCashSaleRequest request
    ) {
        return cashSaleService.createCashSale(tenantId, branchId, userId, request);
    }

    @GetMapping("/today")
    public List<SaleResponse> today(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long branchId
    ) {
        return cashSaleService.getTodaySales(tenantId, branchId);
    }

    @GetMapping
    public List<SaleResponse> byRange(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return cashSaleService.getSalesByRange(tenantId, branchId, from, to);
    }

    @GetMapping("/{saleId}")
    public SaleResponse detail(
            @RequestAttribute("tenantId") Long tenantId,
            @PathVariable Long saleId
    ) {
        return cashSaleService.getById(tenantId, saleId);
    }
}