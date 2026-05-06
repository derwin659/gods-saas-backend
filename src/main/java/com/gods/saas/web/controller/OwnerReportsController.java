package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.response.*;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.impl.OwnerReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;

@RestController
@RequestMapping("/api/owner/reports")
@RequiredArgsConstructor
public class OwnerReportsController {

    private final OwnerReportsService ownerReportsService;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/profitability")
    public ProfitabilityReportResponse getProfitabilityReport(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_PROFITABILITY");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getProfitabilityReport(tenantId, effectiveBranchIdForReports(authentication, branchId), from, to);
    }

    @GetMapping("/sales")
    public OwnerSalesReportResponse getSalesReport(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_ACCESS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getSalesReport(tenantId, effectiveBranchIdForReports(authentication, branchId), from, to);
    }

    @GetMapping("/sales/barber/{barberId}")
    public List<BarberSaleDetailResponse> getBarberSalesDetail(
            Authentication authentication,
            @PathVariable Long barberId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_BARBER_PAYMENTS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBarberSalesDetail(tenantId, effectiveBranchIdForReports(authentication, branchId), barberId, from, to);
    }

    @GetMapping("/branches/summary")
    public BranchSummaryResponse getBranchSummary(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_ACCESS");
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
        adminPermissionService.checkPermission("REPORTS_ACCESS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBranchDetail(tenantId, requireAllowedBranch(authentication, branchId), from, to);
    }

    @GetMapping("/branches/{branchId}/barbers")
    public List<BarberSalesSummaryResponse> getBranchBarbersReport(
            Authentication authentication,
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_BARBER_PAYMENTS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBranchBarbersReport(tenantId, requireAllowedBranch(authentication, branchId), from, to);
    }

    @GetMapping("/barbers/summary")
    public List<BarberSalesSummaryResponse> getBarberSummary(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_BARBER_PAYMENTS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBarberSummary(tenantId, effectiveBranchIdForReports(authentication, branchId), from, to);
    }

    @GetMapping("/barbers/{barberId}/detail")
    public List<BarberSaleDetailResponse> getBarberDetail(
            Authentication authentication,
            @PathVariable Long barberId,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_BARBER_PAYMENTS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getBarberDetail(tenantId, effectiveBranchIdForReports(authentication, branchId), barberId, from, to);
    }

    @GetMapping("/sales/daily")
    public List<DailySalesPointResponse> getDailySales(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_ACCESS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getDailySales(tenantId, effectiveBranchIdForReports(authentication, branchId), from, to);
    }

    @GetMapping("/services/top")
    public List<TopServiceResponse> getTopServices(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_ACCESS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getTopServices(tenantId, effectiveBranchIdForReports(authentication, branchId), from, to);
    }

    @GetMapping("/payments/summary")
    public PaymentSummaryResponse getPaymentSummary(
            Authentication authentication,
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        adminPermissionService.checkPermission("REPORTS_ACCESS");
        Long tenantId = extractTenantId(authentication);
        return ownerReportsService.getPaymentSummary(tenantId, effectiveBranchIdForReports(authentication, branchId), from, to);
    }


    private Long effectiveBranchIdForReports(Authentication authentication, Long requestedBranchId) {
        String role = extractRole(authentication);
        if ("ADMIN".equalsIgnoreCase(role)) {
            Long sessionBranchId = extractBranchId(authentication);
            if (sessionBranchId == null) {
                throw new AccessDeniedException("El administrador no tiene una sede asignada");
            }
            return sessionBranchId;
        }
        return requestedBranchId;
    }

    private Long requireAllowedBranch(Authentication authentication, Long requestedBranchId) {
        String role = extractRole(authentication);
        if ("ADMIN".equalsIgnoreCase(role)) {
            Long sessionBranchId = extractBranchId(authentication);
            if (sessionBranchId == null || !sessionBranchId.equals(requestedBranchId)) {
                throw new AccessDeniedException("El administrador solo puede ver reportes de su sede");
            }
        }
        return requestedBranchId;
    }

    private String extractRole(Authentication authentication) {
        Map<String, Object> details = getDetails(authentication);
        Object role = details.get("role");
        return role == null ? "" : role.toString().trim().toUpperCase();
    }

    private Long extractBranchId(Authentication authentication) {
        Map<String, Object> details = getDetails(authentication);
        return toLong(details.get("branchId"));
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