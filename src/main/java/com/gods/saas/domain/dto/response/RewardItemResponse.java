package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardItemResponse {

    private Long id;
    private String titulo;
    private String descripcion;
    private int costoPuntos;
    private boolean destacado;
    private Integer stock;
    private String imagenUrl;
    private Boolean activo;

    // Constructor anterior para no romper código existente que todavía envía 5 campos.
    public RewardItemResponse(
            Long id,
            String titulo,
            String descripcion,
            int costoPuntos,
            boolean destacado
    ) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.costoPuntos = costoPuntos;
        this.destacado = destacado;
        this.stock = null;
        this.imagenUrl = null;
        this.activo = true;
    }
}