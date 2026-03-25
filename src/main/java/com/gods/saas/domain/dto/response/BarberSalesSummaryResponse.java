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
    private BigDecimal totalSales;
    private Long salesCount;
    private BigDecimal averageTicket;
}