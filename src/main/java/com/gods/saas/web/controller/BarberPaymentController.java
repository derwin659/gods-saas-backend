package com.gods.saas.web.controller;

import com.gods.saas.security.BranchAccessGuard;

import com.gods.saas.domain.dto.AppUserResponse;
import com.gods.saas.domain.dto.request.CreateBarberPaymentRequest;
import com.gods.saas.domain.dto.response.BarberPaymentPreviewResponse;
import com.gods.saas.domain.dto.response.BarberPaymentResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.RoleType;
import com.gods.saas.domain.repository.UserTenantRoleRepository;
import com.gods.saas.service.impl.AdminPermissionService;
import com.gods.saas.service.impl.impl.BarberPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class BarberPaymentController {

    private final BarberPaymentService barberPaymentService;
    private final BranchAccessGuard branchAccessGuard;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final AdminPermissionService adminPermissionService;

    @GetMapping("/barber-payments/payable-employees")
    public List<AppUserResponse> payableEmployees(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId
    ) {
        adminPermissionService.checkPermission("CASH_ACCESS");
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);

        return Stream.of(RoleType.ADMIN, RoleType.CASHIER, RoleType.BARBER)
                .flatMap(role -> userTenantRoleRepository
                        .findActiveUsersByTenantBranchAndRole(tenantId, effectiveBranchId, role)
                        .stream())
                .filter(user -> Boolean.TRUE.equals(user.getSalaryMode()))
                .filter(user -> user.getFixedSalaryAmount() != null
                        && user.getFixedSalaryAmount().signum() > 0
                        && user.getSalaryFrequency() != null)
                .collect(java.util.stream.Collectors.toMap(
                        AppUser::getId,
                        user -> {
                            AppUserResponse response = AppUserResponse.from(user);
                            response.setBranchIds(List.of(effectiveBranchId));
                            return response;
                        },
                        (first, ignored) -> first,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }
    @GetMapping("/barber-payments/preview")
    public BarberPaymentPreviewResponse preview(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam Long barberUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return barberPaymentService.preview(
                tenantId, effectiveBranchId, barberUserId, periodFrom, periodTo
        );
    }

    @PostMapping("/cash-registers/{cashRegisterId}/barber-payments")
    public BarberPaymentResponse createPayment(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestAttribute("userId") Long userId,
            @PathVariable Long cashRegisterId,
            @RequestParam(required = false) Long branchId,
            @RequestBody CreateBarberPaymentRequest request
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return barberPaymentService.createPayment(
                tenantId, effectiveBranchId, cashRegisterId, userId, request
        );
    }

    @GetMapping("/barber-payments/history")
    public List<BarberPaymentResponse> history(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam Long barberUserId
    ) {
        Long effectiveBranchId = branchAccessGuard.resolve(branchId, sessionBranchId);
        return barberPaymentService.history(tenantId, effectiveBranchId, barberUserId);
    }
}