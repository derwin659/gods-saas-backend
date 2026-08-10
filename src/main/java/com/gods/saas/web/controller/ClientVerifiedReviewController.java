package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.CreateVerifiedReviewRequest;
import com.gods.saas.domain.dto.response.VerifiedReviewResponse;
import com.gods.saas.service.impl.VerifiedBusinessReviewService;
import com.gods.saas.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/clients/reviews") @RequiredArgsConstructor
public class ClientVerifiedReviewController {
    private final VerifiedBusinessReviewService service;
    private final JwtUtil jwtUtil;
    @PostMapping
    public ResponseEntity<VerifiedReviewResponse> create(@RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateVerifiedReviewRequest request) {
        String token = authHeader.replace("Bearer ", "");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(
                jwtUtil.getTenantIdFromToken(token), jwtUtil.getCustomerIdFromToken(token), request));
    }
    @GetMapping("/appointment/{appointmentId}")
    public VerifiedReviewResponse find(@RequestHeader("Authorization") String authHeader,
            @PathVariable Long appointmentId) {
        String token = authHeader.replace("Bearer ", "");
        return service.find(jwtUtil.getTenantIdFromToken(token), jwtUtil.getCustomerIdFromToken(token), appointmentId);
    }
    @GetMapping("/mine")
    public java.util.List<java.util.Map<String,Object>> mine(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return service.myReviews(jwtUtil.getTenantIdFromToken(token), jwtUtil.getCustomerIdFromToken(token));
    }
    @GetMapping("/public")
    public java.util.List<java.util.Map<String,Object>> publicReviews(@RequestHeader("Authorization") String authHeader,
            @RequestParam(required=false) Long branchId,@RequestParam(required=false) Long professionalId) {
        String token = authHeader.replace("Bearer ", "");
        return service.publicReviews(jwtUtil.getTenantIdFromToken(token), branchId, professionalId);
    }}