package com.gods.saas.domain.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BranchDashboardItemResponse {
    private Long branchId;
    private String branchName;
    private BigDecimal todaySales;
    private Integer todayAppointments;
    private Integer activeBarbers;
    private BigDecimal averageTicket;
}
