package com.gods.saas.domain.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorteRecomendadoDto {

    private String nombre;
    private Double score;    // 0.0 - 1.0
    private String riesgo;   // bajo | medio | alto
}

