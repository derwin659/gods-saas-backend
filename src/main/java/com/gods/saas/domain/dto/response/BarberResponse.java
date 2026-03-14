package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BarberResponse {
    private Long userId;
    private String nombre;
    private String apellido;
    private String email;
    private String phone;
    private String rol;
    private Boolean activo;
    private Long branchId;
    private String branchNombre;
}
