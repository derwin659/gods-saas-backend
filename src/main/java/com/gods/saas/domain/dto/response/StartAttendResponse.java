package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StartAttendResponse {
    private Long appointmentId;
    private Long customerId;
    private Long barberUserId;
    private Long serviceId;
    private String customerName;
    private String serviceName;
    private String fecha;
    private String horaInicio;
    private String horaFin;
    private String estado;
    private Integer duracionMinutos;
    private boolean walkIn;
}