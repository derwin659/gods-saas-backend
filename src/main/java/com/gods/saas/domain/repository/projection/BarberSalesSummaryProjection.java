package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;

public interface BarberSalesSummaryProjection {
    Long getBarberId();
    String getBarberName();
    BigDecimal getTotalSales();
    Long getSalesCount();
}