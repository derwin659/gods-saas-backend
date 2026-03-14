package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class CrearUsuarioRequest {
    private String nombre;
    private String apellido;
    private String email;
    private String phone;
    private String rol;      // BARBER, ADMIN, OWNER, CASHIER
    private Long branchId;
    private String password;
}

