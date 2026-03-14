package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class UpdateTenantUserRequest {
    private String nombre;
    private String phone;
    private String role;     // si quieres permitir cambiar rol dentro del tenant
    private Boolean active;  // activar/desactivar usuario
}

