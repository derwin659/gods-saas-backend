package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ApprovePaymentRequest;
import com.gods.saas.domain.model.Subscription;
import com.gods.saas.service.impl.impl.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/subscription")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/approve-payment")
    public ResponseEntity<Subscription> approvePayment(
            @RequestHeader("X-User-Id") Long reviewedByUserId,
            @RequestBody ApprovePaymentRequest request
    ) {
        return ResponseEntity.ok(
                subscriptionService.approveManualPayment(request.getPaymentId(), reviewedByUserId)
        );
    }
}