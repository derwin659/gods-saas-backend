package com.gods.saas.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerSalesReportResponse {
    private BigDecimal totalSales;
    private Long totalSalesCount;
    private BigDecimal averageTicket;
    private Long activeBarbers;

    /** Ventas realmente cobradas. No incluye FREE / GRATIS / CORTESIA. */
    private Long paidSalesCount;

    /** Cantidad de ventas de cortesía en el rango. */
    private Long courtesySalesCount;

    /** Valor referencial de los items regalados. No suma a ingresos. */
    private BigDecimal courtesyReferenceAmount;

    private List<BarberSalesSummaryResponse> barberSummaries;
}
