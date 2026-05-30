package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberAgendaItemResponse {

    private Long appointmentId;
    private Long customerId;

    private String hora;
    private String horaFin;
    private String cliente;
    private String servicio;
    private String estado;
    private String telefono;
    private String fecha;

    private String internalNote;
    private String notaInterna;
    private String notes;
}
