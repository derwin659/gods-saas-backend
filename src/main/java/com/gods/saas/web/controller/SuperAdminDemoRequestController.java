package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ReviewDemoRequest;
import com.gods.saas.domain.dto.response.DemoRequestResponse;
import com.gods.saas.service.impl.impl.DemoRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/super-admin/demo-requests")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class SuperAdminDemoRequestController {

    private final DemoRequestService demoRequestService;

    @GetMapping
    public ResponseEntity<List<DemoRequestResponse>> findAll() {
        return ResponseEntity.ok(demoRequestService.findAll());
    }

    @GetMapping("/pending")
    public ResponseEntity<List<DemoRequestResponse>> findPending() {
        return ResponseEntity.ok(demoRequestService.findPending());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemoRequestResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(demoRequestService.findById(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<DemoRequestResponse> approve(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDemoRequest request
    ) {
        return ResponseEntity.ok(demoRequestService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<DemoRequestResponse> reject(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDemoRequest request
    ) {
        return ResponseEntity.ok(demoRequestService.reject(id, request));
    }

    @PostMapping("/{id}/suspicious")
    public ResponseEntity<DemoRequestResponse> suspicious(
            @PathVariable Long id,
            @RequestBody(required = false) ReviewDemoRequest request
    ) {
        return ResponseEntity.ok(demoRequestService.markSuspicious(id, request));
    }
}