package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.CustomerCutHistoryResponse;
import com.gods.saas.service.impl.CustomerCutHistoryService;
import com.gods.saas.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owner/customers")
@RequiredArgsConstructor
public class OwnerCustomerHistoryController {

    private final CustomerCutHistoryService customerCutHistoryService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "Obtener el último corte del cliente")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{customerId}/last-cut")
    public ResponseEntity<CustomerCutHistoryResponse> getLastCut(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId
    ) {
        Long tenantId = extractTenantId(authHeader);
        return ResponseEntity.ok(customerCutHistoryService.getLastByCustomer(tenantId, customerId));
    }

    @Operation(summary = "Listar historial de cortes del cliente")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{customerId}/cut-history")
    public ResponseEntity<List<CustomerCutHistoryResponse>> listCutHistory(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Long tenantId = extractTenantId(authHeader);
        return ResponseEntity.ok(customerCutHistoryService.listByCustomer(tenantId, customerId, limit));
    }

    private Long extractTenantId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getTenantIdFromToken(token);
    }
}
