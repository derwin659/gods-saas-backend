package com.gods.saas.domain.repository.projection;


import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailySalesReportProjection {
    LocalDate getSaleDate();
    BigDecimal getTotalSales();
    Long getSalesCount();
}