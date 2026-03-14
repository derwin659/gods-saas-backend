package com.gods.saas.domain.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class VentaRapidaRequest {
    private String nombre;
    private String apellido;
    private String phone;
    private LocalDate fechaNacimiento;
    private String origenCliente; // Facebook, Instagram, Tiktok, Amigo, Otros
    private Long tenantId;
    private Long branchId;
}

