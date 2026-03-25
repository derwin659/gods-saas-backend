package com.gods.saas.domain.repository.projection;

public interface BranchReportSummaryProjection {
    Long getBranchId();
    String getBranchName();
    java.math.BigDecimal getTotalSales();
    Long getTotalServices();
    Long getTotalClients();
    java.math.BigDecimal getAverageTicket();
}