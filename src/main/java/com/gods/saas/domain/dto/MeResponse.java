package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class MeResponse {
    private Long userId;
    private String nombre;
    private String apellido;
    private String phone;
    private String email;
    private Long tenantId;
    private Long branchId;
    private String rol;
    private String tipoUsuario;
}

