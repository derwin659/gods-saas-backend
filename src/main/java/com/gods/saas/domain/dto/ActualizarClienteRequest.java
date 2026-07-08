package com.gods.saas.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ActualizarClienteRequest {

    private String nombre;
    private String apellido;
    private String telefono; // ✅ NUEVO
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
