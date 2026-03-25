package com.gods.saas.domain.dto.response;


import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BarberReportDetailResponse {
    private Long barberId;
    private String barberName;
    private Long branchId;
    private String branchName;
    private BigDecimal totalSales;
    private Long totalServices;
    private Long totalClients;
    private BigDecimal averageTicket;
    private BigDecimal commission;
    private List<BarberSaleDetailResponse> sales;
}