package com.gods.saas.domain.dto.response;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorteUx {

    private String nombre;
    private String nombreVisible;
    private Double score;
    private List<String> razones;
    private String mantenimiento;
    private String largoMinimo;
    private Boolean vistaGenerativaDisponible;
}

