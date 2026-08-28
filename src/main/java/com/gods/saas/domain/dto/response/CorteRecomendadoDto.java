package com.gods.saas.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorteRecomendadoDto {

    private String nombre;
    @JsonProperty("nombre_visible")
    private String nombreVisible;
    private Double score;    // 0.0 - 1.0
    private String riesgo;   // bajo | medio | alto
    private List<String> razones;
    private String mantenimiento;
    @JsonProperty("largo_minimo")
    private String largoMinimo;
    @JsonProperty("vista_generativa_disponible")
    private Boolean vistaGenerativaDisponible;
}

