package com.gods.saas.domain.dto.response;


import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchReportSummaryResponse {
    private Long branchId;
    private String branchName;
    private BigDecimal totalSales;
    private Long totalServices;
    private Long totalClients;
    private BigDecimal averageTicket;
    private BigDecimal totalCommission;
    private String topBarberName;
}