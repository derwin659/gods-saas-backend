package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateQuickCustomerRequest;
import com.gods.saas.domain.dto.response.CustomerSearchResponse;
import com.gods.saas.service.impl.impl.CustomerSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerSearchController {

    private final CustomerSearchService customerSearchService;

    @GetMapping("/search")
    public List<CustomerSearchResponse> search(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestParam String q
    ) {
        return customerSearchService.search(tenantId, q);
    }

    @PostMapping("/quick")
    public CustomerSearchResponse createQuick(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestBody CreateQuickCustomerRequest request
    ) {
        return customerSearchService.createQuick(tenantId, request);
    }
}