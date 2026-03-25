package com.gods.saas.domain.dto.response;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CustomerSearchResponse {
    private Long customerId;
    private String nombreCompleto;
    private String telefono;
    private Integer puntosDisponibles;
}