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
    private List<BarberSalesSummaryResponse> barberSummaries;
}