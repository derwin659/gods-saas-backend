package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ChangePlanRequest;
import com.gods.saas.domain.dto.request.ReportPaymentRequest;
import com.gods.saas.domain.dto.response.SubscriptionCurrentResponse;
import com.gods.saas.service.impl.impl.SubscriptionService;
import com.gods.saas.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final JwtUtil jwtUtil;

    @GetMapping("/current")
    public SubscriptionCurrentResponse getCurrentSubscription(HttpServletRequest request) {
        Long tenantId = extractTenantId(request);
        return subscriptionService.getCurrentSubscriptionResponse(tenantId);
    }

    @PutMapping("/change-plan")
    public SubscriptionCurrentResponse changePlan(
            @RequestBody ChangePlanRequest request,
            HttpServletRequest httpRequest
    ) {
        Long tenantId = extractTenantId(httpRequest);
        subscriptionService.changePlan(tenantId, request.getPlan());
        return subscriptionService.getCurrentSubscriptionResponse(tenantId);
    }

    @PostMapping("/report-payment")
    public ResponseEntity<String> reportPayment(
            @RequestBody ReportPaymentRequest request,
            HttpServletRequest httpRequest
    ) {
        Long tenantId = extractTenantId(httpRequest);
        subscriptionService.reportManualPayment(tenantId, request);
        return ResponseEntity.ok("Pago reportado correctamente");
    }

    private Long extractTenantId(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtil.getTenantIdFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT no enviado o inválido");
        }
        return authHeader.substring(7);
    }
}