package com.gods.saas.domain.dto.response;

public record OwnerBranchResponse(
        Long branchId,
        String nombre,
        String direccion,
        String telefono,
        Boolean activo,
        String imageUrl,
        String ciudad,
        Double latitude,
        Double longitude,
        Boolean publicVisible,
        Boolean directoryEnabled,
        String publicDescription,
        Long followerCount,
        Long directoryViews,
        Long routeOpens,
        Long bookingIntents,
        Long confirmedBookings,
        Double bookingConversionRate
) {
}