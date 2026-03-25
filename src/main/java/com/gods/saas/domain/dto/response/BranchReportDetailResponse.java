package com.gods.saas.domain.dto.response;


import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchReportDetailResponse {
    private BranchReportSummaryResponse summary;
    private List<BarberReportSummaryResponse> barbers;
    private List<DailySalesReportResponse> dailySales;
    private List<TopServiceReportResponse> topServices;
    private List<PaymentMethodSummaryResponse> paymentMethods;
}