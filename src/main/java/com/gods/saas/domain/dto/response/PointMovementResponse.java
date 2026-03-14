package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PointMovementResponse {
    private String fecha;
    private String descripcion;
    private int puntos;
    private boolean positivo;
}
