package com.gods.saas.domain.repository.projection;


import java.math.BigDecimal;

public interface TopServiceReportProjection {
    String getServiceName();
    Long getTimesSold();
    BigDecimal getTotalAmount();
}