package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyProfitabilityProjection {
    LocalDate getReportDate();
    BigDecimal getTotalSales();
    BigDecimal getAdditionalIncome();
    BigDecimal getOperationalExpenses();
    BigDecimal getBarberAdvances();
    BigDecimal getBarberPayments();
}
