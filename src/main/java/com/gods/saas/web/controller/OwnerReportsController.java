package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.*;
import com.gods.saas.service.impl.impl.OwnerReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/reports")
@RequiredArgsConstructor
public class OwnerReportsController {

    private final OwnerReportsService ownerReportsService;

    @GetMapping("/profitability")
    public ProfitabilityReportResponse getProfitabilityReport(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getProfitabilityReport(tenantId, branchId, from, to);
    }

    @GetMapping("/sales")
    public OwnerSalesReportResponse getSalesReport(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getSalesReport(tenantId, branchId, from, to);
    }

    @GetMapping("/sales/barber/{barberId}")
    public List<BarberSaleDetailResponse> getBarberSalesDetail(
            Authentication authentication,
            @PathVariable Long barberId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBarberSalesDetail(tenantId, branchId, barberId, from, to);
    }

    @GetMapping("/branches/summary")
    public BranchSummaryResponse getBranchSummary(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBranchSummary(tenantId, from, to);
    }

    @GetMapping("/branches/{branchId}/detail")
    public BranchDetailResponse getBranchDetail(
            Authentication authentication,
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBranchDetail(tenantId, branchId, from, to);
    }

    @GetMapping("/branches/{branchId}/barbers")
    public List<BarberSalesSummaryResponse> getBranchBarbersReport(
            Authentication authentication,
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBranchBarbersReport(tenantId, branchId, from, to);
    }

    @GetMapping("/barbers/summary")
    public List<BarberSalesSummaryResponse> getBarberSummary(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBarberSummary(tenantId, branchId, from, to);
    }

    @GetMapping("/barbers/{barberId}/detail")
    public List<BarberSaleDetailResponse> getBarberDetail(
            Authentication authentication,
            @PathVariable Long barberId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBarberDetail(tenantId, branchId, barberId, from, to);
    }

    @GetMapping("/sales/daily")
    public List<DailySalesPointResponse> getDailySales(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getDailySales(tenantId, branchId, from, to);
    }

    @GetMapping("/services/top")
    public List<TopServiceResponse> getTopServices(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getTopServices(tenantId, branchId, from, to);
    }

    @GetMapping("/payments/summary")
    public PaymentSummaryResponse getPaymentSummary(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getPaymentSummary(tenantId, branchId, from, to);
    }

    private Long extractTenantId(Authentication authentication) {
        Map<String, Object> details = getDetails(authentication);
        return toLong(details.get("tenantId"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDetails(Authentication authentication) {
        Object details = authentication.getDetails();

        if (!(details instanceof Map<?, ?> map)) {
            throw new IllegalStateException("No se encontraron detalles en el token");
        }

        return (Map<String, Object>) map;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new IllegalArgumentException("No se pudo convertir a Long: " + value);
    }
}