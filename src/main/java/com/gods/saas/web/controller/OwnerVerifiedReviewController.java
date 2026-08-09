package com.gods.saas.web.controller;

import com.gods.saas.security.SecurityUtils;
import com.gods.saas.domain.dto.request.OwnerReviewReplyRequest;
import com.gods.saas.domain.dto.request.ReportVerifiedReviewRequest;
import jakarta.validation.Valid;
import com.gods.saas.service.impl.VerifiedBusinessReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/owner/reviews")
@RequiredArgsConstructor
public class OwnerVerifiedReviewController {
    private final VerifiedBusinessReviewService service;

    @GetMapping
    public Map<String, Object> inbox(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Integer rating) {
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("La calificación debe estar entre 1 y 5");
        }
        return service.ownerInbox(SecurityUtils.getCurrentTenantId(), branchId, rating);
    }

    @PutMapping("/{reviewId}/reply")
    public Map<String, Object> reply(
            @PathVariable Long reviewId,
            @Valid @RequestBody OwnerReviewReplyRequest request) {
        return service.reply(
                SecurityUtils.getCurrentTenantId(),
                SecurityUtils.getCurrentUserId(),
                reviewId,
                request.reply());
    }
    @PostMapping("/{reviewId}/report")
    public Map<String, Object> report(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReportVerifiedReviewRequest request) {
        return service.report(
                SecurityUtils.getCurrentTenantId(),
                SecurityUtils.getCurrentUserId(),
                reviewId,
                request.reason(),
                request.details());
    }}