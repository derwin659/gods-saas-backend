package com.gods.saas.domain.dto.response;

import java.util.List;
import java.time.LocalDateTime;

public record PublicAffiliatedBranchDetailResponse(
        PublicAffiliatedBranchResponse branch,
        Boolean openNow,
        String openStatusLabel,
        String todayHours,
        List<PublicServiceSummary> services,
        List<PublicPromotionSummary> promotions,
        List<PublicReviewSummary> reviews
) {
    public record PublicServiceSummary(
            Long id,
            String name,
            String description,
            String category,
            Integer durationMinutes,
            Double price,
            Boolean variablePrice,
            String imageUrl
    ) {}

    public record PublicReviewSummary(
            Long id,
            Integer rating,
            String comment,
            String customerDisplayName,
            LocalDateTime createdAt,
            Boolean verified,
            String ownerReply,
            LocalDateTime ownerRepliedAt
    ) {}

    public record PublicPromotionSummary(
            Long id,
            String title,
            String subtitle,
            String description,
            String badge,
            String imageUrl,
            String priceText
    ) {}
}