package com.gods.saas.domain.dto.response;

public record ClientBusinessSummaryResponse(
        Long tenantId,
        String tenantName,
        String tenantCode,
        String tenantLogoUrl,
        String businessType,
        String city,
        Long customerId,
        Integer availablePoints,
        Integer accumulatedPoints,
        Long nextAppointmentId,
        String nextAppointmentDate,
        String nextAppointmentTime,
        String nextAppointmentService,
        String nextAppointmentBranch,
        String lastVisitDate,
        Long completedVisits,
        String relationLabel
) {
}
