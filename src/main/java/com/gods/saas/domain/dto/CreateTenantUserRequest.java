package com.gods.saas.domain.dto;


import lombok.Data;

@Data
public class CreateTenantUserRequest {
    private String nombre;     // "Pedro"
    private String email;      // "pedro@gmail.com"
    private String password;   // "123456"
    private String phone;      // opcional
    private String role;       // "TENANT_ADMIN" | "BARBER" | "CAJA" | "ADMIN"
}

