package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FinishAttendResponse {
    private Long appointmentId;
    private String estado;
    private String fecha;
    private String horaInicio;
    private String horaFin;
}