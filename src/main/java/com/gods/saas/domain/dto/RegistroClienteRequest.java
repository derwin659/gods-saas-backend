package com.gods.saas.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RegistroClienteRequest {

    private String nombre;          // requerido
    private String apellido;        // opcional
    private String phone;           // requerido
    private LocalDate fechaNacimiento; // opcional
    private String origenCliente;   // Facebook, Instagram, Tiktok, Amigo, Otros
    private Long branchId;          // opcional: sede donde se registró
}
