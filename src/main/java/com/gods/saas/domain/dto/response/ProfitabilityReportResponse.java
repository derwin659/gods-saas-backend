package com.gods.saas.domain.dto.response;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ProfitabilityReportResponse {
    private BigDecimal totalSales;
    private BigDecimal cashSales;
    private BigDecimal nonCashSales;

    private BigDecimal operationalExpenses;
    private BigDecimal barberAdvances;
    private BigDecimal barberPayments;

    private BigDecimal netProfit;
    private BigDecimal profitMargin;

    private List<DailyProfitabilityPointResponse> dailyProfitability;
}