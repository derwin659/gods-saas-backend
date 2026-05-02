package com.gods.saas.domain.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RewardItemResponse {
    private Long id;
    private String titulo;
    private String descripcion;
    private int costoPuntos;
    private boolean destacado;
    private Integer stock;
    private String imagenUrl;
    private Boolean activo;

    public RewardItemResponse(
            Long id,
            String titulo,
            String descripcion,
            int costoPuntos,
            boolean destacado
    ) {
        this(id, titulo, descripcion, costoPuntos, destacado, null, null, true);
    }

    @lombok.Builder
    public RewardItemResponse(
            Long id,
            String titulo,
            String descripcion,
            int costoPuntos,
            boolean destacado,
            Integer stock,
            String imagenUrl,
            Boolean activo
    ) {
        this.id = id;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.costoPuntos = costoPuntos;
        this.destacado = destacado;
        this.stock = stock;
        this.imagenUrl = imagenUrl;
        this.activo = activo;
    }
}
