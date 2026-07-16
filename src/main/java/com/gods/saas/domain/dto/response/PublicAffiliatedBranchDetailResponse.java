package com.gods.saas.domain.dto.response;

import java.util.List;

public record PublicAffiliatedBranchDetailResponse(
        PublicAffiliatedBranchResponse branch,
        Boolean openNow,
        String openStatusLabel,
        String todayHours,
        List<PublicServiceSummary> services,
        List<PublicPromotionSummary> promotions
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