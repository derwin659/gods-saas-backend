package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CustomerFollowUpRequest;
import com.gods.saas.domain.dto.request.CustomerFollowUpStatusRequest;
import com.gods.saas.domain.dto.response.CustomerFollowUpResponse;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.CustomerFollowUpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/owner/customers/{customerId}/follow-ups")
public class OwnerCustomerFollowUpController {

    private final CustomerFollowUpService customerFollowUpService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping
    public List<CustomerFollowUpResponse> list(
            @PathVariable Long customerId,
            Authentication authentication
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");
        return customerFollowUpService.list(tenantId(authentication), customerId);
    }

    @PostMapping
    public CustomerFollowUpResponse create(
            @PathVariable Long customerId,
            @RequestBody CustomerFollowUpRequest request,
            Authentication authentication
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");
        return customerFollowUpService.create(
                tenantId(authentication),
                customerId,
                userId(authentication),
                request
        );
    }

    @PatchMapping("/{followUpId}/status")
    public CustomerFollowUpResponse updateStatus(
            @PathVariable Long customerId,
            @PathVariable Long followUpId,
            @RequestBody CustomerFollowUpStatusRequest request,
            Authentication authentication
    ) {
        adminPermissionService.checkPermission("CUSTOMERS_ACCESS");
        return customerFollowUpService.updateStatus(
                tenantId(authentication),
                customerId,
                followUpId,
                request == null ? null : request.getStatus()
        );
    }

    private Long userId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Number n) return n.longValue();
        return Long.valueOf(principal.toString());
    }

    private Long tenantId(Authentication authentication) {
        Map<String, Object> details = (Map<String, Object>) authentication.getDetails();
        return Long.valueOf(details.get("tenantId").toString());
    }
}
