package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String nombre;
    private String email;
    private String password; // temporal o generado
    private String role;     // ADMIN | BARBER | CAJA
}

