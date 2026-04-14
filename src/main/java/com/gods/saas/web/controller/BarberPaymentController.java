package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateBarberPaymentRequest;
import com.gods.saas.domain.dto.response.BarberPaymentPreviewResponse;
import com.gods.saas.domain.dto.response.BarberPaymentResponse;
import com.gods.saas.service.impl.impl.BarberPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class BarberPaymentController {

    private final BarberPaymentService barberPaymentService;

    @GetMapping("/barber-payments/preview")
    public BarberPaymentPreviewResponse preview(
            @RequestAttribute("tenantId") Long tenantId,
            @RequestAttribute("branchId") Long sessionBranchId,
            @RequestParam(required = false) Long branchId,
            @RequestParam Long barberUserId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo
    ) {
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
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
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
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
        Long effectiveBranchId = branchId != null ? branchId : sessionBranchId;
        return barberPaymentService.history(tenantId, effectiveBranchId, barberUserId);
    }
}