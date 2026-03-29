package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.CustomerCutHistoryResponse;
import com.gods.saas.service.impl.CustomerCutHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class InternalCustomerHistoryController {

    private final CustomerCutHistoryService customerCutHistoryService;

    @GetMapping("/{customerId}/last-cut")
    public ResponseEntity<CustomerCutHistoryResponse> getLastCut(
            @RequestAttribute("tenantId") Long tenantId,
            @PathVariable Long customerId
    ) {
        return ResponseEntity.ok(customerCutHistoryService.getLastByCustomer(tenantId, customerId));
    }

    @GetMapping("/{customerId}/cut-history")
    public ResponseEntity<List<CustomerCutHistoryResponse>> listCutHistory(
            @RequestAttribute("tenantId") Long tenantId,
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(customerCutHistoryService.listByCustomer(tenantId, customerId, limit));
    }
}
