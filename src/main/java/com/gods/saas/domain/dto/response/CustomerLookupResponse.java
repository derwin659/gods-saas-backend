package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerLookupResponse {
    private boolean found;
    private Long id;
    private String nombre;
    private String apellido;
    private String phone;
    private Long tenantId;
    private Integer puntosDisponibles;
    private String mensaje;
}
