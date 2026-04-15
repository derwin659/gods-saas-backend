package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerAgendaResponse {
    private Long appointmentId;
    private Long customerId;
    private Long serviceId;
    private Long barberUserId;
    private Long branchId;

    private String fecha;
    private String hora;
    private String horaFin;

    private String cliente;
    private String servicio;
    private String barbero;
    private String estado;
    private String telefono;
}
