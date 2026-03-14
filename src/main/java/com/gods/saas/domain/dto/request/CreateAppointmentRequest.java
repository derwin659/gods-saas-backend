package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class CreateAppointmentRequest {
    private Long branchId;
    private Long serviceId;
    private Long barberId;   // null = cualquiera
    private String date;     // yyyy-MM-dd
    private String horaInicio; // HH:mm
}
