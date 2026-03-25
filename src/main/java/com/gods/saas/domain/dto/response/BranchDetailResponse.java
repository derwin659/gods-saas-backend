package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BranchDetailResponse {
    private Long branchId;
    private BigDecimal totalSales;
    private Long totalSalesCount;
    private BigDecimal averageTicket;
    private Long activeBarbers;
    private List<BarberSalesSummaryResponse> barbers;
    private List<TopServiceResponse> topServices;
    private List<DailySalesPointResponse> dailySales;
    private PaymentSummaryResponse paymentSummary;
}
