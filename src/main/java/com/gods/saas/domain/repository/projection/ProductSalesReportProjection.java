package com.gods.saas.domain.repository.projection;

import java.math.BigDecimal;

public interface ProductSalesReportProjection {
    Long getProductId();
    String getProductName();
    String getSku();
    String getCategory();
    Long getUnitsSold();
    Long getSalesCount();
    BigDecimal getRevenue();
    BigDecimal getEstimatedCost();
    BigDecimal getEstimatedMargin();
}