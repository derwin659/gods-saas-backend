package com.gods.saas.domain.dto.response;

import java.math.BigDecimal;

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
        boolean destacado,

        /**
         * Campos reales para aplicar descuento en reservas.
         * discountType: AMOUNT / PERCENT / FIXED_PRICE
         */
        String discountType,
        BigDecimal discountValue
) {}
