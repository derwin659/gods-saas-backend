package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ApprovesPaymentRequest;
import com.gods.saas.domain.dto.request.RejectPaymentRequest;
import com.gods.saas.domain.dto.response.SuperAdminPaymentResponse;
import com.gods.saas.service.impl.impl.SuperAdminPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/super-admin/payments")
@RequiredArgsConstructor
public class SuperAdminPaymentController {

    @Autowired
    private final SuperAdminPaymentService servicess;

    @GetMapping("/pending")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SuperAdminPaymentResponse>> findPending() {
        return ResponseEntity.ok(servicess.findPending());
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<SuperAdminPaymentResponse>> findAll() {
        return ResponseEntity.ok(servicess.findAll());
    }

    @PostMapping("/{paymentId}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> approve(@PathVariable Long paymentId,
                                        @RequestBody ApprovesPaymentRequest request) {
        servicess.approve(paymentId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{paymentId}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> reject(@PathVariable Long paymentId,
                                       @RequestBody RejectPaymentRequest request) {
        servicess.reject(paymentId, request);
        return ResponseEntity.ok().build();
    }
}