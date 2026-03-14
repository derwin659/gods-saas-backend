package com.gods.saas.domain.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BarberHomeAppointmentResponse {
    private Long appointmentId;
    private String hora;
    private String cliente;
    private String servicio;
    private String estado;
}
