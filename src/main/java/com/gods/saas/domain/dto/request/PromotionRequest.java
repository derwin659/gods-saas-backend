package com.gods.saas.domain.dto.request;

import com.gods.saas.domain.enums.PromotionRedirectType;
import com.gods.saas.domain.enums.PromotionType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PromotionRequest {

    private Long branchId;

    private String titulo;
    private String subtitulo;
    private String descripcion;

    private PromotionType tipo;
    private String badge;
    private String imageUrl;
    private String iconName;
    private String priceText;
    private String ctaLabel;
    private Boolean sendNotification;
    private PromotionRedirectType redirectType;
    private String redirectValue;

    private Boolean destacado;
    private Boolean soloClientesConPuntos;
    private Integer puntosMinimos;
    private Boolean activo;

    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    private Integer ordenVisual;
}
