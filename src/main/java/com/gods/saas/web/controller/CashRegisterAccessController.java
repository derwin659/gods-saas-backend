package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.CashRegisterResponse;
import com.gods.saas.service.impl.impl.CashRegisterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cash-register")
@RequiredArgsConstructor
public class CashRegisterAccessController {

    private final CashRegisterService cashRegisterService;

    @GetMapping("/current")
    public CashRegisterResponse current(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return cashRegisterService.getCurrent(tenantId, effectiveBranchId);
    }
}