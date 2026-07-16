package com.gods.saas.domain.dto.response;

import java.time.LocalDateTime;

public record VerifiedReviewResponse(
        Long reviewId, Long appointmentId, Long branchId, Integer rating,
        String comment, String customerName, LocalDateTime createdAt
) {}