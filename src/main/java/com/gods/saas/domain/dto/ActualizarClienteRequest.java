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
}