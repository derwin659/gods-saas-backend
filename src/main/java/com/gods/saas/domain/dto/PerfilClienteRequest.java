package com.gods.saas.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PerfilClienteRequest {

    private String nombre;
    private String apellido;
    private String email;
    private LocalDate fechaNacimiento;
    private String origenCliente;
}

