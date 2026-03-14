package com.gods.saas.domain.dto.response;

public record ClientPromotionResponse(
        Long id,
        String titulo,
        String subtitulo,
        String descripcion,
        String badge,
        String tipo,
        String iconName,
        String imageUrl,
        String priceText,
        String ctaLabel,
        String redirectType,
        String redirectValue,
        boolean destacado
) {}