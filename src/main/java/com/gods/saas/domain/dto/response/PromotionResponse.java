package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionResponse {

    private Long id;
    private Long tenantId;
    private Long branchId;

    private String titulo;
    private String subtitulo;
    private String descripcion;

    private String tipo;
    private String badge;
    private String imageUrl;
    private String iconName;
    private String priceText;
    private String ctaLabel;

    private String redirectType;
    private String redirectValue;

    private boolean destacado;
    private boolean soloClientesConPuntos;
    private Integer puntosMinimos;
    private boolean activo;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    private Integer ordenVisual;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
