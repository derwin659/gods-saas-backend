package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarberSalesSummaryResponse {
    private Long barberId;
    private String barberName;

    /** Producción cobrada real. No incluye cortesías. */
    private BigDecimal totalSales;

    /** Total de ventas donde participó el barbero, incluyendo cortesías. */
    private Long salesCount;

    private BigDecimal averageTicket;

    /** Ventas cobradas. */
    private Long paidSalesCount;

    /** Cortesías del barbero en el rango. */
    private Long courtesySalesCount;

    /** Valor referencial de cortesías del barbero. No suma a caja. */
    private BigDecimal courtesyReferenceAmount;
}
