package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CloseCashRegisterRequest;
import com.gods.saas.domain.dto.request.OpenCashRegisterRequest;
import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.service.impl.impl.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
            @RequestAttribute("branchId") Long branchId,
            @RequestAttribute("userId") Long userId,
            @RequestBody OpenCashRegisterRequest request
    ) {
        return cashRegisterService.open(tenantId, branchId, userId, request);
    }

    @GetMapping("/current")
    public CashRegisterResponse current(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long branchId
    ) {
        return cashRegisterService.getCurrent(tenantId, branchId);
    }

    @PostMapping("/{cashRegisterId}/close")
    public CashRegisterResponse close(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long branchId,
            @PathVariable Long cashRegisterId,
            @RequestBody CloseCashRegisterRequest request
    ) {
        return cashRegisterService.close(tenantId, branchId, cashRegisterId, request);
    }

    @GetMapping("/history")
    public List<CashRegisterResponse> history(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return cashRegisterService.history(tenantId, branchId, from, to);
    }
}