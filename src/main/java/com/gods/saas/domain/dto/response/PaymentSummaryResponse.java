package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PaymentSummaryResponse {
    private BigDecimal cash;
    private BigDecimal yape;
    private BigDecimal plin;
    /** Monto cobrado por FREE. Normalmente debe ser 0. */
    private BigDecimal free;
    private BigDecimal card;
    private BigDecimal transfer;
    private BigDecimal total;

    /** Cantidad de cortesías en el rango filtrado. */
    private Long freeCount;

    /** Valor referencial de cortesías. No suma al total cobrado. */
    private BigDecimal freeReferenceAmount;
}
