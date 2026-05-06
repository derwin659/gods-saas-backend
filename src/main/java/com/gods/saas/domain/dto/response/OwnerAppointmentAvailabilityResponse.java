package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerAppointmentAvailabilityResponse {
    private String fecha;
    private Long branchId;
    private Long barberUserId;
    private Long serviceId;
    private Integer serviceDurationMinutes;
    private List<OwnerAppointmentSlotResponse> slots;
}
