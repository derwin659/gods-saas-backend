package com.gods.saas.domain.dto.request;

import lombok.Data;

@Data
public class RewardItemRequest {
    private String nombre;
    private String descripcion;
    private Integer puntosRequeridos;
    private Integer stock;
    private String imagenUrl;
    private Boolean activo;
}
