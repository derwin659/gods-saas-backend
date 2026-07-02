package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class DailyProfitabilityPointResponse {
    private LocalDate date;
    private BigDecimal totalSales;
    private BigDecimal additionalIncome;
    private BigDecimal operationalExpenses;
    private BigDecimal barberAdvances;
    private BigDecimal barberPayments;
    private BigDecimal barberCommissionsAccrued;
    private BigDecimal cashFlowAfterBarberSettlements;
    private BigDecimal netProfit;
}
