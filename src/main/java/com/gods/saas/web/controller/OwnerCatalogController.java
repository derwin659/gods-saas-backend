package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.SimpleBarberResponse;
import com.gods.saas.domain.dto.response.SimpleCustomerResponse;
import com.gods.saas.domain.dto.response.SimpleServiceResponse;
import com.gods.saas.service.impl.impl.OwnerCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/catalog")
@RequiredArgsConstructor
public class OwnerCatalogController {

    private final OwnerCatalogService ownerCatalogService;

    @GetMapping("/barbers")
    public List<SimpleBarberResponse> getBarbers(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return ownerCatalogService.getBarbers(tenantId, effectiveBranchId);
    }

    @GetMapping("/services")
    public List<SimpleServiceResponse> getServices(
            @RequestAttribute("tenantId") Long tenantId
    ) {
        return ownerCatalogService.getServices(tenantId);
    }

    @GetMapping("/customers/search")
    public List<SimpleCustomerResponse> searchCustomers(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestParam String q
    ) {
        return ownerCatalogService.searchCustomers(tenantId, q);
    }
}
