package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabelloDto {

    private String densidad;           // baja | media | alta
    private String textura;            // lacio | ondulado | rizado | afro
    private String largo;              // rapado | corto | medio | largo
    private Double confianza;          // 0.0 - 1.0
    private OnduladoDto ondulado;
}
