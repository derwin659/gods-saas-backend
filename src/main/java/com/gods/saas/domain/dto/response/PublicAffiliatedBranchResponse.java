package com.gods.saas.domain.dto.response;

public record PublicAffiliatedBranchResponse(
        Long tenantId,
        String tenantName,
        String tenantCode,
        String tenantLogoUrl,
        String businessType,
        Long branchId,
        String branchName,
        String address,
        String phone,
        String city,
        Double latitude,
        Double longitude,
        String imageUrl,
        String publicDescription,
        Double distanceKm,
        String distanceLabel,
        String availabilityLabel,
        Boolean near,
        Double ratingAverage,
        Long reviewCount
) {
}