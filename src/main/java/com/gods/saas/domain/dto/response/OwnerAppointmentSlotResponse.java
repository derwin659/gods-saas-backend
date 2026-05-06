package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerAppointmentSlotResponse {
    private String hora;
    private String horaFin;
    private Boolean available;
    private String reason;
    private Long appointmentId;
}
