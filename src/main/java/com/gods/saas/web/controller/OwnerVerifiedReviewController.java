package com.gods.saas.web.controller;

import com.gods.saas.security.SecurityUtils;
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
}