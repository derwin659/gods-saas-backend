package com.gods.saas.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ActualizarClienteRequest {

@JsonAlias("nombres")
    private String nombre;
@JsonAlias("apellidos")
    private String apellido;
    private String telefono; // âœ… NUEVO
    private String email;
    private LocalDate fechaNacimiento;
    private String origenCliente;
    private Long branchId;
    private String customerNotes;
    private String preferredServices;
    private String customerRestrictions;
    private String preferredContactChannel;
    private String favoriteBarberName;
}
