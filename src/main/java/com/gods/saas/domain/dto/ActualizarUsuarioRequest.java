package com.gods.saas.domain.dto;

import lombok.Data;

@Data
public class ActualizarUsuarioRequest {
    private String nombre;
    private String apellido;
    private String phone;
    private Long branchId;
    private String rol;
}
