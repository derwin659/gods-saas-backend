package com.gods.saas.web.controller;

import com.gods.saas.domain.dto.request.ModerateVerifiedReviewRequest;
import com.gods.saas.security.SecurityUtils;
import com.gods.saas.service.impl.VerifiedBusinessReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/super-admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminReviewModerationController {
    private final VerifiedBusinessReviewService service;

    @GetMapping
    public Map<String, Object> inbox(@RequestParam(required = false) String status) {
        return service.moderationInbox(status);
    }

    @PutMapping("/{reviewId}/moderate")
    public Map<String, Object> moderate(
            @PathVariable Long reviewId,
            @Valid @RequestBody ModerateVerifiedReviewRequest request) {
        return service.moderate(
                SecurityUtils.getCurrentUserId(), reviewId, request.status(), request.note());
    }
}