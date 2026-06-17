package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ChangePlanRequest;
import com.gods.saas.domain.dto.request.AppStorePurchaseVerifyRequest;
import com.gods.saas.domain.dto.request.ReportPaymentRequest;
import com.gods.saas.domain.dto.request.SubscriptionCheckoutRequest;
import com.gods.saas.domain.dto.response.AppStoreProductResponse;
import com.gods.saas.domain.dto.response.SubscriptionCheckoutResponse;
import com.gods.saas.domain.dto.response.SubscriptionCurrentResponse;
import com.gods.saas.domain.dto.response.SubscriptionPlanPriceResponse;
import com.gods.saas.service.impl.impl.SubscriptionService;
import com.gods.saas.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/plan-prices")
    public List<SubscriptionPlanPriceResponse> getPlanPrices(HttpServletRequest request) {
        Long tenantId = extractTenantId(request);
        return subscriptionService.getPlanPrices(tenantId);
    }

    @GetMapping("/app-store/products")
    public List<AppStoreProductResponse> getAppStoreProducts() {
        return subscriptionService.getAppStoreProducts();
    }

    @PostMapping("/app-store/verify")
    public SubscriptionCurrentResponse verifyAppStorePurchase(
            @RequestBody AppStorePurchaseVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        Long tenantId = extractTenantId(httpRequest);
        return subscriptionService.verifyAppStorePurchase(tenantId, request);
    }

    @PostMapping("/checkout")
    public SubscriptionCheckoutResponse createCheckout(
            @RequestBody SubscriptionCheckoutRequest request,
            HttpServletRequest httpRequest
    ) {
        Long tenantId = extractTenantId(httpRequest);
        return subscriptionService.createInternationalCheckout(tenantId, request);
    }

    private Long extractTenantId(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtil.getTenantIdFromToken(token);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Token JWT no enviado o invÃ¡lido");
        }
        return authHeader.substring(7);
    }
}
